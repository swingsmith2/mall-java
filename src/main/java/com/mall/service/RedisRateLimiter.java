package com.mall.service;

import com.mall.config.MallProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisRateLimiter {

    private final StringRedisTemplate redis;
    private final MallProperties mallProperties;

    /**
     * 滑动窗口近似：每分钟允许的次数，基于 Redis INCR + TTL。
     */
    public void checkOrderLimit(String userId) {
        String key = "rl:order:" + userId;
        Long n = redis.opsForValue().increment(key);
        if (n != null && n == 1) {
            redis.expire(key, Duration.ofMinutes(1));
        }
        int max = mallProperties.getRateLimit().getOrdersPerMinute();
        if (n != null && n > max) {
            throw new com.mall.common.BusinessException(429, "下单过于频繁，请稍后再试");
        }
    }
}
