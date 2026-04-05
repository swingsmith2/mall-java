package com.mall.domain;

import lombok.Data;

@Data
public class OrderItem {
    private Long id;
    private Long orderId;
    private Long productId;
    private Integer qty;
    private Long priceCent;
}
