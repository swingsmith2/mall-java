package com.mall.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OrderNotificationService {

    @Async("mallAsyncExecutor")
    public void onOrderCreated(Long orderId, Long userId, Long totalCent) {
        log.info("[异步] 订单已创建 orderId={} userId={} totalCent={}", orderId, userId, totalCent);
    }
}
