package com.mall.domain;

import lombok.Data;

import java.time.Instant;

@Data
public class Shop {
    private Long id;
    private Long ownerId;
    private String name;
    private String description;
    private String status;
    private String forcedCloseReason;
    private Instant createdAt;
}
