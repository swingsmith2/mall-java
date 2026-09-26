package com.mall.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.config.MallProperties;
import com.mall.domain.SeckillActivity;
import com.mall.domain.SeckillQueueMessage;
import com.mall.mapper.OrderMapper;
import com.mall.mapper.SeckillActivityMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillOrderWorker {

    private final SeckillActivityMapper activityMapper;
    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final SeckillInventory inventory;
    private final ObjectMapper objectMapper;
    private final MallProperties mallProperties;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelayString = "${mall.seckill.poll-ms:200}")
    public void scheduledPoll() {
        poll();
    }

    public int poll() {
        int persisted = 0;
        for (SeckillActivity activity : activityMapper.listRunning()) {
            recover(activity.getId());
            ensureRedis(activity);
            persisted += drain(activity.getId());
        }
        closeDue();
        return persisted;
    }

    private void ensureRedis(SeckillActivity activity) {
        if (inventory.metaExists(activity.getId())) {
            return;
        }
        int sold = orderMapper.sumActiveSeckillQty(activity.getId());
        int queued = inventory.queueSize(activity.getId());
        inventory.armRemaining(activity, activity.getStock() - sold - queued);
    }

    private int drain(long activityId) {
        int persisted = 0;
        Set<String> seen = new HashSet<>();
        while (true) {
            String raw = inventory.moveQueueToProcessing(activityId);
            if (raw == null) {
                return persisted;
            }
            SeckillQueueMessage message;
            try {
                message = objectMapper.readValue(raw, SeckillQueueMessage.class);
            } catch (Exception ex) {
                inventory.removeProcessing(activityId, raw);
                log.error("drop malformed seckill payload {}", raw, ex);
                continue;
            }
            if (!seen.add(message.getToken())) {
                inventory.removeProcessing(activityId, raw);
                inventory.requeue(activityId, raw);
                return persisted;
            }
            try {
                Long orderId = orderService.placeSeckillOrder(message);
                inventory.setResult(message.getUserId(), message.getToken(), "ORDER:" + orderId);
                inventory.removeProcessing(activityId, raw);
                meterRegistry.counter("mall.seckill.persisted").increment();
                persisted++;
            } catch (RuntimeException ex) {
                inventory.removeProcessing(activityId, raw);
                int next = message.getAttempts() + 1;
                if (next >= mallProperties.getSeckill().getMaxAttempts()) {
                    compensate(message);
                    log.warn("seckill persist failed permanently token={}", message.getToken(), ex);
                } else {
                    message.setAttempts(next);
                    try {
                        inventory.requeue(activityId, objectMapper.writeValueAsString(message));
                    } catch (Exception writeEx) {
                        compensate(message);
                        log.error("seckill requeue failed token={}", message.getToken(), writeEx);
                    }
                }
            }
        }
    }

    private void recover(long activityId) {
        while (inventory.moveProcessingToQueueHead(activityId) != null) {
            // 上次进程中断留下的处理中消息放回队列头
        }
    }

    private void compensate(SeckillQueueMessage message) {
        boolean restored = inventory.restoreToRedis(message.getActivityId(), message.getUserId(), message.getQty());
        if (!restored) {
            orderService.returnSeckillStockToProduct(message.getProductId(), message.getQty());
        }
        inventory.setResult(message.getUserId(), message.getToken(), SeckillInventory.FAILED);
        meterRegistry.counter("mall.seckill.persist_failed").increment();
    }

    private void closeDue() {
        Instant now = Instant.now();
        for (SeckillActivity activity : activityMapper.listRunning()) {
            if (activity.getEndAt().isAfter(now)) {
                continue;
            }
            inventory.closeGate(activity.getId());
            drain(activity.getId());
            if (inventory.queueSize(activity.getId()) > 0) {
                continue;
            }
            int leftover = inventory.takeLeftover(activity.getId());
            if (leftover > 0) {
                orderService.returnSeckillStockToProduct(activity.getProductId(), leftover);
            }
            activityMapper.markEnded(activity.getId());
        }
    }
}
