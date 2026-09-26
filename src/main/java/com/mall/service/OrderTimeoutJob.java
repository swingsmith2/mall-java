package com.mall.service;

import com.mall.config.MallProperties;
import com.mall.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTimeoutJob {

    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final MallProperties mallProperties;

    @Scheduled(fixedDelayString = "${mall.order.close-scan-ms:5000}")
    public void scheduledClose() {
        closeExpired();
    }

    public int closeExpired() {
        Instant deadline = Instant.now().minus(mallProperties.getOrder().getPayTimeout());
        List<Long> ids = orderMapper.listUnpaidIdsCreatedBefore(deadline, 50);
        int closed = 0;
        for (Long id : ids) {
            try {
                if (orderService.cancelIfUnpaid(id)) {
                    closed++;
                }
            } catch (RuntimeException ex) {
                log.warn("close unpaid order {} failed", id, ex);
            }
        }
        return closed;
    }
}
