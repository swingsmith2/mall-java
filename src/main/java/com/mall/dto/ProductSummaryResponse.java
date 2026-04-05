package com.mall.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductSummaryResponse {
    private Long id;
    private String name;
    private Long priceCent;
    private Integer stock;
}
