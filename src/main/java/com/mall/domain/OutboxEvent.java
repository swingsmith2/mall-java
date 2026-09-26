package com.mall.domain;

import lombok.Data;

import java.time.Instant;

@Data
public class OutboxEvent {
    private Long id;
    private String eventType;
    private Long aggregateId;
    private String payload;
    private String status;
    private int attempts;
    private Instant nextAttemptAt;
    private String lastError;
    private Instant createdAt;
    private Instant sentAt;
}
