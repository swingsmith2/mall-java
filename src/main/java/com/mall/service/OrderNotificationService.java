package com.mall.service;

import com.mall.domain.OutboxEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OrderNotificationService {

    public void deliver(OutboxEvent event) {
        log.info("[outbox] type={} aggregateId={} payload={}",
                event.getEventType(), event.getAggregateId(), event.getPayload());
    }
}
