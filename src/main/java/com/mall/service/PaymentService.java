package com.mall.service;

import com.mall.common.BusinessException;
import com.mall.common.OrderExpiredException;
import com.mall.dto.OrderResponse;
import com.mall.dto.PaymentCallbackRequest;
import com.mall.payment.PaymentSigner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentSigner paymentSigner;
    private final OrderService orderService;

    public OrderResponse handleCallback(String signature, PaymentCallbackRequest req) {
        if (!"SUCCESS".equals(req.getStatus())) {
            throw new BusinessException("仅接受成功支付回调");
        }
        if (!paymentSigner.matches(signature, req.getOrderId(), req.getPaymentNo(), req.getAmountCent(), req.getStatus())) {
            throw new BusinessException(401, "支付回调签名无效");
        }
        try {
            return orderService.applyPayment(req);
        } catch (OrderExpiredException e) {
            throw new BusinessException(409, e.getMessage());
        }
    }
}
