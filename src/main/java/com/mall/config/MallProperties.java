package com.mall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "mall")
public class MallProperties {

    private Jwt jwt = new Jwt();
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class Jwt {
        private String secret = "change-me";
        private long expirationMs = 86400000L;
    }

    @Data
    public static class RateLimit {
        private int ordersPerMinute = 30;
    }
}
