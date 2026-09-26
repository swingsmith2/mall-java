package com.mall.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class DisputeResponse {
    private Long id;
    private Long orderId;
    private Long openerId;
    private String reason;
    private String status;
    private String resolution;
    private String resolutionNote;
    private Long handlerId;
    private Instant createdAt;
    private Instant resolvedAt;
}
