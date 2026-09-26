package com.mall.controller;

import com.mall.common.ApiResponse;
import com.mall.dto.OrderResponse;
import com.mall.dto.PaymentCallbackRequest;
import com.mall.payment.PaymentSigner;
import com.mall.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/callback")
    public ApiResponse<OrderResponse> callback(
            @RequestHeader(value = PaymentSigner.HEADER, required = false) String signature,
            @Valid @RequestBody PaymentCallbackRequest req) {
        return ApiResponse.ok(paymentService.handleCallback(signature, req));
    }
}
