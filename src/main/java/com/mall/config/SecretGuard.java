package com.mall.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SecretGuard {

    static final Set<String> DENY = Set.of(
            "change-me",
            "change-me-to-a-long-random-secret-at-least-256-bits"
    );

    private final MallProperties mallProperties;

    @PostConstruct
    public void verify() {
        require("JWT_SECRET / mall.jwt.secret", mallProperties.getJwt().getSecret());
        require("PAY_CALLBACK_SECRET / mall.payment.callback-secret", mallProperties.getPayment().getCallbackSecret());
    }

    static void require(String name, String value) {
        if (value == null || value.isBlank()
                || value.getBytes(StandardCharsets.UTF_8).length < 32
                || DENY.contains(value)) {
            throw new IllegalStateException(name
                    + " 未设置或过弱：至少 32 字节，且不能使用示例占位符。可执行 scripts/run.sh 生成 .env.local");
        }
    }
}
