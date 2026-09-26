package com.mall.dto;

import com.mall.domain.PayChannel;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SimulatedPayRequest {
    @NotNull(message = "支付渠道不能为空")
    private PayChannel channel;
}
