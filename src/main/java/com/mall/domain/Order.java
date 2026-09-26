package com.mall.domain;

import lombok.Data;

import java.time.Instant;

@Data
public class Order {
    private Long id;
    private Long userId;
    private Long totalCent;
    private String status;
    private String idempotentKey;
    private Long seckillActivityId;
    private Instant createdAt;
    private Instant paidAt;
    private Instant cancelledAt;
}
