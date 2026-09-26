package com.mall.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.domain.OutboxEvent;
import com.mall.mapper.OutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public void enqueue(String eventType, Long aggregateId, Map<String, ?> payload) {
        OutboxEvent event = new OutboxEvent();
        event.setEventType(eventType);
        event.setAggregateId(aggregateId);
        try {
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化 outbox 事件", e);
        }
        outboxMapper.insert(event);
    }
}
