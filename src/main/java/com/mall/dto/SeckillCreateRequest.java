package com.mall.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

@Data
public class SeckillCreateRequest {
    @NotNull(message = "商品不能为空")
    private Long productId;

    @NotNull(message = "秒杀价不能为空")
    @Min(value = 0, message = "秒杀价不能为负")
    private Long seckillPriceCent;

    @NotNull(message = "秒杀库存不能为空")
    @Min(value = 1, message = "秒杀库存至少为 1")
    private Integer stock;

    @NotNull(message = "开始时间不能为空")
    private Instant startAt;

    @NotNull(message = "结束时间不能为空")
    private Instant endAt;

    @Min(value = 1, message = "限购至少为 1")
    private Integer perUserLimit = 1;
}
