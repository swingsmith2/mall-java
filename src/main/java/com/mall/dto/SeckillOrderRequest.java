package com.mall.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SeckillOrderRequest {
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为 1")
    private Integer qty;

    @Size(max = 64)
    @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "幂等键只能包含字母、数字、下划线和短横线")
    private String idempotentKey;
}
