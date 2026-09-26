package com.mall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PaymentCallbackRequest {
    @NotNull(message = "订单号不能为空")
    private Long orderId;

    @NotBlank(message = "支付单号不能为空")
    @Size(max = 64, message = "支付单号过长")
    private String paymentNo;

    @NotNull(message = "金额不能为空")
    private Long amountCent;

    @NotBlank(message = "支付状态不能为空")
    private String status;
}
