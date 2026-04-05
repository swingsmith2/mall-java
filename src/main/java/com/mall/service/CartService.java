package com.mall.service;

import com.mall.dto.CartItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class CartService {

    private static final String KEY_PREFIX = "cart:";
    private static final long TTL_DAYS = 7;

    private final StringRedisTemplate redis;

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    public void addItem(Long userId, Long productId, int qty) {
        if (qty <= 0) {
            return;
        }
        String k = key(userId);
        String field = String.valueOf(productId);
        Object curObj = redis.opsForHash().get(k, field);
        String cur = curObj == null ? null : curObj.toString();
        int n = cur == null ? 0 : Integer.parseInt(cur);
        redis.opsForHash().put(k, field, String.valueOf(n + qty));
        redis.expire(k, TTL_DAYS, TimeUnit.DAYS);
    }

    public List<CartItemResponse> list(Long userId) {
        String k = key(userId);
        Map<Object, Object> entries = redis.opsForHash().entries(k);
        List<CartItemResponse> list = new ArrayList<>();
        for (Map.Entry<Object, Object> e : entries.entrySet()) {
            list.add(new CartItemResponse(Long.parseLong(e.getKey().toString()), Integer.parseInt(e.getValue().toString())));
        }
        return list;
    }

    public void removeLine(Long userId, Long productId) {
        redis.opsForHash().delete(key(userId), String.valueOf(productId));
    }

    public void clear(Long userId) {
        redis.delete(key(userId));
    }
}
