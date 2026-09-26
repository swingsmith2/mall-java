package com.mall.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class SeckillActivityResponse {
    private Long id;
    private Long productId;
    private Long seckillPriceCent;
    private Integer stock;
    private Integer remaining;
    private Instant startAt;
    private Instant endAt;
    private Integer perUserLimit;
    private String status;
}
