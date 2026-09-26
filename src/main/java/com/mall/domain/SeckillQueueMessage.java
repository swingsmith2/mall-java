package com.mall.domain;

import lombok.Data;

@Data
public class SeckillQueueMessage {
    private Long activityId;
    private Long userId;
    private Long productId;
    private Integer qty;
    private Long priceCent;
    private String token;
    private int attempts;
}
