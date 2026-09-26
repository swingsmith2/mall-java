package com.mall.domain;

import lombok.Data;

import java.time.Instant;

@Data
public class Payment {
    private Long id;
    private Long orderId;
    private String paymentNo;
    private Long amountCent;
    private String status;
    private Instant createdAt;
}
