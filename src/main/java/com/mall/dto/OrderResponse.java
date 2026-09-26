package com.mall.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class OrderResponse {
    private Long id;
    private Long totalCent;
    private String status;
    private Instant createdAt;
    private Instant paidAt;
    private Instant cancelledAt;
    private Instant payDeadline;
    private List<OrderLineResponse> lines;

    @Data
    @Builder
    public static class OrderLineResponse {
        private Long productId;
        private Integer qty;
        private Long priceCent;
    }
}
