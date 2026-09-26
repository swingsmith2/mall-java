package com.mall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "mall")
public class MallProperties {

    private Jwt jwt = new Jwt();
    private RateLimit rateLimit = new RateLimit();
    private Order order = new Order();
    private Payment payment = new Payment();
    private Outbox outbox = new Outbox();
    private Alert alert = new Alert();
    private Seckill seckill = new Seckill();

    @Data
    public static class Jwt {
        private String secret = "";
        private long expirationMs = 86400000L;
    }

    @Data
    public static class RateLimit {
        private int ordersPerMinute = 30;
        private int loginsPerMinute = 20;
    }

    @Data
    public static class Order {
        private Duration payTimeout = Duration.ofMinutes(15);
        private long closeScanMs = 5000L;
    }

    @Data
    public static class Payment {
        private String callbackSecret = "";
    }

    @Data
    public static class Outbox {
        private long pollMs = 1000L;
        private int maxAttempts = 8;
        private int claimLeaseSeconds = 30;
    }

    @Data
    public static class Alert {
        private long scanMs = 15000L;
        private Duration pendingStale = Duration.ofSeconds(60);
    }

    @Data
    public static class Seckill {
        private long pollMs = 200L;
        private int maxAttempts = 5;
        private Duration resultTtl = Duration.ofHours(24);
    }
}
