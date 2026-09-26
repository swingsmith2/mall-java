package com.mall.service;

import com.mall.config.MallProperties;
import com.mall.domain.OutboxEvent;
import com.mall.mapper.OutboxMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxDispatcher {

    private final OutboxMapper outboxMapper;
    private final OrderNotificationService notificationService;
    private final MallProperties mallProperties;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelayString = "${mall.outbox.poll-ms:1000}")
    public void scheduledPoll() {
        poll();
    }

    public int poll() {
        int lease = mallProperties.getOutbox().getClaimLeaseSeconds();
        int maxAttempts = mallProperties.getOutbox().getMaxAttempts();
        List<OutboxEvent> batch = outboxMapper.claim(20, lease);
        for (OutboxEvent event : batch) {
            try {
                notificationService.deliver(event);
                outboxMapper.markSent(event.getId());
                meterRegistry.counter("mall.outbox.sent").increment();
            } catch (RuntimeException ex) {
                int nextAttempts = event.getAttempts() + 1;
                int delay = Math.min(60, Math.max(1, 1 << Math.min(event.getAttempts(), 6)));
                String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                if (message.length() > 500) {
                    message = message.substring(0, 500);
                }
                outboxMapper.markRetry(event.getId(), message, delay, maxAttempts);
                if (nextAttempts >= maxAttempts) {
                    meterRegistry.counter("mall.outbox.failed").increment();
                }
                log.warn("outbox deliver failed id={} attempts={}", event.getId(), nextAttempts, ex);
            }
        }
        return batch.size();
    }
}
