package com.mall.service;

import com.mall.common.BusinessException;
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

    public void checkOrderLimit(String userId) {
        hit("rl:order:" + userId, mallProperties.getRateLimit().getOrdersPerMinute(), "下单过于频繁，请稍后再试");
    }

    public void checkLoginLimit(String username) {
        hit("rl:login:" + username, mallProperties.getRateLimit().getLoginsPerMinute(), "登录过于频繁，请稍后再试");
    }

    private void hit(String key, int max, String message) {
        Long n = redis.opsForValue().increment(key);
        if (n != null && (n == 1L || redis.getExpire(key) < 0)) {
            redis.expire(key, Duration.ofMinutes(1));
        }
        if (n != null && n > max) {
            throw new BusinessException(429, message);
        }
    }
}
