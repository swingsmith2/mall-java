package com.mall.payment;

import com.mall.config.MallProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class PaymentSigner {

    public static final String HEADER = "X-Mall-Signature";

    private final MallProperties mallProperties;

    public String sign(long orderId, String paymentNo, long amountCent, String status) {
        return sign(mallProperties.getPayment().getCallbackSecret(), orderId, paymentNo, amountCent, status);
    }

    public boolean matches(String provided, long orderId, String paymentNo, long amountCent, String status) {
        if (provided == null || provided.isBlank()) {
            return false;
        }
        byte[] expected = sign(orderId, paymentNo, amountCent, status).getBytes(StandardCharsets.UTF_8);
        byte[] actual = provided.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    public static String sign(String secret, long orderId, String paymentNo, long amountCent, String status) {
        String canonical = orderId + "|" + paymentNo + "|" + amountCent + "|" + status;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算支付回调签名", e);
        }
    }
}
