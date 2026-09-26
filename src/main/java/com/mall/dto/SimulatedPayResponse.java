package com.mall.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class SimulatedPayResponse {
    private String channel;
    private Long orderId;
    private String status;
    private Map<String, Object> payload;
}
