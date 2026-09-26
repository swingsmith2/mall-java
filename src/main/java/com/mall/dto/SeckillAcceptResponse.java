package com.mall.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SeckillAcceptResponse {
    private Long activityId;
    private String token;
    private String status;
    private Long orderId;
}
