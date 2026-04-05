package com.mall.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PlaceOrderRequest {
    @NotEmpty
    @Valid
    private List<OrderLineRequest> items;

    @Size(max = 128)
    private String idempotentKey;
}
