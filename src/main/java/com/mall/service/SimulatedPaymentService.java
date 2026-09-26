package com.mall.service;

import com.mall.common.BusinessException;
import com.mall.domain.Order;
import com.mall.domain.OrderStatus;
import com.mall.domain.PayChannel;
import com.mall.dto.OrderResponse;
import com.mall.dto.PaymentCallbackRequest;
import com.mall.dto.SimulatedPayResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SimulatedPaymentService {

    private final OrderService orderService;

    public SimulatedPayResponse pay(Long orderId, PayChannel channel) {
        Order order = orderService.requireOwned(orderId);
        if (!OrderStatus.CREATED.name().equals(order.getStatus())) {
            throw new BusinessException(409, "订单当前状态不可支付");
        }
        String tradeNo = switch (channel) {
            case ALIPAY -> "2026" + orderId + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            case WECHAT -> "420000" + orderId + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            case BANK_CARD -> "Q" + orderId + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        };
        PaymentCallbackRequest callback = new PaymentCallbackRequest();
        callback.setOrderId(orderId);
        callback.setPaymentNo(tradeNo);
        callback.setAmountCent(order.getTotalCent());
        callback.setStatus("SUCCESS");
        OrderResponse paid = orderService.applyPayment(callback);
        return SimulatedPayResponse.builder()
                .channel(channel.name())
                .orderId(paid.getId())
                .status(paid.getStatus())
                .payload(payload(channel, order, tradeNo))
                .build();
    }

    private Map<String, Object> payload(PayChannel channel, Order order, String tradeNo) {
        String outTradeNo = "MALL" + order.getId();
        return switch (channel) {
            case ALIPAY -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("code", "10000");
                body.put("msg", "Success");
                body.put("out_trade_no", outTradeNo);
                body.put("trade_no", tradeNo);
                body.put("total_amount", centsToYuan(order.getTotalCent()));
                body.put("trade_status", "TRADE_SUCCESS");
                Map<String, Object> envelope = new LinkedHashMap<>();
                envelope.put("alipay_trade_pay_response", body);
                envelope.put("sign", "SIMULATED");
                yield envelope;
            }
            case WECHAT -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("return_code", "SUCCESS");
                body.put("return_msg", "OK");
                body.put("result_code", "SUCCESS");
                body.put("appid", "wx-mall-sim");
                body.put("mch_id", "1900000109");
                body.put("out_trade_no", outTradeNo);
                body.put("transaction_id", tradeNo);
                body.put("trade_state", "SUCCESS");
                body.put("total_fee", order.getTotalCent());
                yield body;
            }
            case BANK_CARD -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("respCode", "00");
                body.put("respMsg", "交易成功");
                body.put("orderId", outTradeNo);
                body.put("queryId", tradeNo);
                body.put("txnAmt", String.valueOf(order.getTotalCent()));
                body.put("txnType", "01");
                body.put("currencyCode", "156");
                yield body;
            }
        };
    }

    private static String centsToYuan(Long cents) {
        long value = cents == null ? 0 : cents;
        return (value / 100) + "." + String.format("%02d", Math.abs(value % 100));
    }
}
