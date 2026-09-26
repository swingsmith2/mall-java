package com.mall.domain;

import lombok.Data;

import java.time.Instant;

@Data
public class SeckillActivity {
    private Long id;
    private Long productId;
    private Long seckillPriceCent;
    private Integer stock;
    private Instant startAt;
    private Instant endAt;
    private Integer perUserLimit;
    private String status;
    private Instant createdAt;
}
