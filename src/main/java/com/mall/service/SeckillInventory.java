package com.mall.service;

import com.mall.config.MallProperties;
import com.mall.domain.SeckillActivity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SeckillInventory {

    static final String QUEUED = "QUEUED";
    static final String FAILED = "FAILED";

    private static final RedisScript<List> ACCEPT = RedisScript.of("""
            local meta = KEYS[6]
            if redis.call('EXISTS', meta) == 0 then
              return {-5, ''}
            end
            if redis.call('HGET', meta, 'closed') == '1' then
              return {-4, ''}
            end
            local now = tonumber(ARGV[1])
            if now < tonumber(redis.call('HGET', meta, 'start')) then
              return {-3, ''}
            end
            if now >= tonumber(redis.call('HGET', meta, 'end')) then
              return {-4, ''}
            end
            if redis.call('EXISTS', KEYS[3]) == 1 then
              return {2, redis.call('GET', KEYS[3])}
            end
            local qty = tonumber(ARGV[2])
            local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
            if stock < 0 then
              return {-5, ''}
            end
            if stock < qty then
              return {-1, ''}
            end
            local limit = tonumber(redis.call('HGET', meta, 'limit'))
            local bought = tonumber(redis.call('GET', KEYS[2]) or '0')
            if bought + qty > limit then
              return {-2, ''}
            end
            local price = redis.call('HGET', meta, 'price')
            local productId = redis.call('HGET', meta, 'product')
            local token = ARGV[3]
            local payload = string.format(
              '{"activityId":%d,"userId":%d,"productId":%d,"qty":%d,"priceCent":%d,"token":"%s","attempts":0}',
              tonumber(ARGV[5]), tonumber(ARGV[4]), tonumber(productId), qty, tonumber(price), token)
            redis.call('DECRBY', KEYS[1], qty)
            redis.call('INCRBY', KEYS[2], qty)
            redis.call('EXPIRE', KEYS[2], tonumber(ARGV[6]))
            redis.call('SET', KEYS[3], token, 'EX', ARGV[6])
            redis.call('RPUSH', KEYS[4], payload)
            redis.call('SET', KEYS[5], 'QUEUED', 'EX', ARGV[6])
            return {1, token}
            """, List.class);

    private static final RedisScript<Long> RESTORE = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
              return 0
            end
            if redis.call('HGET', KEYS[1], 'closed') == '1' then
              return 0
            end
            local qty = tonumber(ARGV[1])
            redis.call('INCRBY', KEYS[2], qty)
            local bought = tonumber(redis.call('GET', KEYS[3]) or '0')
            if bought < qty then
              qty = bought
            end
            if qty > 0 then
              redis.call('DECRBY', KEYS[3], qty)
            end
            return 1
            """, Long.class);

    private static final RedisScript<Long> TAKE_LEFTOVER = RedisScript.of("""
            local n = tonumber(redis.call('GET', KEYS[1]) or '0')
            redis.call('SET', KEYS[1], '0')
            return n
            """, Long.class);

    private static final RedisScript<String> MOVE = RedisScript.of("""
            return redis.call('LMOVE', KEYS[1], KEYS[2], ARGV[1], ARGV[2])
            """, String.class);

    private final StringRedisTemplate redis;
    private final MallProperties mallProperties;

    public void arm(SeckillActivity activity) {
        String stockKey = stockKey(activity.getId());
        Boolean created = redis.opsForValue().setIfAbsent(stockKey, String.valueOf(activity.getStock()));
        if (Boolean.TRUE.equals(created) || !Boolean.TRUE.equals(redis.hasKey(metaKey(activity.getId())))) {
            writeMeta(activity, false);
        }
    }

    public void armRemaining(SeckillActivity activity, int remaining) {
        redis.opsForValue().setIfAbsent(stockKey(activity.getId()), String.valueOf(Math.max(remaining, 0)));
        if (!Boolean.TRUE.equals(redis.hasKey(metaKey(activity.getId())))) {
            writeMeta(activity, false);
        }
    }

    public List<?> accept(long activityId, long userId, int qty, String token, String idempotentKey, long nowMillis) {
        long ttl = mallProperties.getSeckill().getResultTtl().toSeconds();
        return redis.execute(
                ACCEPT,
                List.of(
                        stockKey(activityId),
                        boughtKey(activityId, userId),
                        idemKey(activityId, userId, idempotentKey),
                        queueKey(activityId),
                        resultKey(userId, token),
                        metaKey(activityId)),
                String.valueOf(nowMillis),
                String.valueOf(qty),
                token,
                String.valueOf(userId),
                String.valueOf(activityId),
                String.valueOf(ttl));
    }

    /**
     * @return true 表示件数已回到 Redis 秒杀库存；false 表示活动已关闭或 Redis 中已无活动，调用方应回补商品库存
     */
    public boolean restoreToRedis(long activityId, long userId, int qty) {
        Long n = redis.execute(
                RESTORE,
                List.of(metaKey(activityId), stockKey(activityId), boughtKey(activityId, userId)),
                String.valueOf(qty));
        return n != null && n == 1L;
    }

    public void closeGate(long activityId) {
        redis.opsForHash().put(metaKey(activityId), "closed", "1");
    }

    public int takeLeftover(long activityId) {
        Long n = redis.execute(TAKE_LEFTOVER, List.of(stockKey(activityId)));
        return n == null ? 0 : n.intValue();
    }

    public String moveQueueToProcessing(long activityId) {
        return redis.execute(MOVE, List.of(queueKey(activityId), processingKey(activityId)), "LEFT", "RIGHT");
    }

    public String moveProcessingToQueueHead(long activityId) {
        return redis.execute(MOVE, List.of(processingKey(activityId), queueKey(activityId)), "RIGHT", "LEFT");
    }

    public void removeProcessing(long activityId, String payload) {
        redis.opsForList().remove(processingKey(activityId), 1, payload);
    }

    public void requeue(long activityId, String payload) {
        redis.opsForList().rightPush(queueKey(activityId), payload);
    }

    public int queueSize(long activityId) {
        Long n = redis.opsForList().size(queueKey(activityId));
        return n == null ? 0 : n.intValue();
    }

    public Integer remaining(long activityId) {
        String v = redis.opsForValue().get(stockKey(activityId));
        return v == null ? null : Integer.valueOf(v);
    }

    public String result(long userId, String token) {
        return redis.opsForValue().get(resultKey(userId, token));
    }

    public void setResult(long userId, String token, String value) {
        long ttl = mallProperties.getSeckill().getResultTtl().toSeconds();
        redis.opsForValue().set(resultKey(userId, token), value, java.time.Duration.ofSeconds(ttl));
    }

    public boolean metaExists(long activityId) {
        return Boolean.TRUE.equals(redis.hasKey(metaKey(activityId)));
    }

    private void writeMeta(SeckillActivity activity, boolean closed) {
        String meta = metaKey(activity.getId());
        redis.opsForHash().put(meta, "start", String.valueOf(activity.getStartAt().toEpochMilli()));
        redis.opsForHash().put(meta, "end", String.valueOf(activity.getEndAt().toEpochMilli()));
        redis.opsForHash().put(meta, "limit", String.valueOf(activity.getPerUserLimit()));
        redis.opsForHash().put(meta, "price", String.valueOf(activity.getSeckillPriceCent()));
        redis.opsForHash().put(meta, "product", String.valueOf(activity.getProductId()));
        redis.opsForHash().put(meta, "closed", closed ? "1" : "0");
    }

    private static String stockKey(long activityId) {
        return "seckill:stock:" + activityId;
    }

    private static String boughtKey(long activityId, long userId) {
        return "seckill:bought:" + activityId + ":" + userId;
    }

    private static String idemKey(long activityId, long userId, String idempotentKey) {
        return "seckill:idem:" + activityId + ":" + userId + ":" + idempotentKey;
    }

    private static String queueKey(long activityId) {
        return "seckill:queue:" + activityId;
    }

    private static String processingKey(long activityId) {
        return "seckill:processing:" + activityId;
    }

    private static String resultKey(long userId, String token) {
        return "seckill:result:" + userId + ":" + token;
    }

    private static String metaKey(long activityId) {
        return "seckill:meta:" + activityId;
    }
}
