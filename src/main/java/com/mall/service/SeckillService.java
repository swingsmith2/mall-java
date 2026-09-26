package com.mall.service;

import com.mall.common.AfterCommit;
import com.mall.common.BusinessException;
import com.mall.domain.Product;
import com.mall.domain.SeckillActivity;
import com.mall.domain.Shop;
import com.mall.domain.User;
import com.mall.dto.SeckillAcceptResponse;
import com.mall.dto.SeckillActivityResponse;
import com.mall.dto.SeckillCreateRequest;
import com.mall.dto.SeckillOrderRequest;
import com.mall.mapper.ProductMapper;
import com.mall.mapper.SeckillActivityMapper;
import com.mall.mapper.ShopMapper;
import com.mall.mapper.UserMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeckillService {

    private final SeckillActivityMapper activityMapper;
    private final ProductMapper productMapper;
    private final ShopMapper shopMapper;
    private final UserMapper userMapper;
    private final ProductService productService;
    private final SeckillInventory inventory;
    private final MeterRegistry meterRegistry;

    public List<SeckillActivityResponse> listOpen() {
        return activityMapper.listRunning().stream().map(this::toResponse).toList();
    }

    public SeckillActivityResponse get(long id) {
        SeckillActivity activity = activityMapper.findById(id);
        if (activity == null) {
            throw new BusinessException(404, "秒杀活动不存在");
        }
        return toResponse(activity);
    }

    @Transactional
    public SeckillActivityResponse create(SeckillCreateRequest req) {
        if (!req.getEndAt().isAfter(req.getStartAt())) {
            throw new BusinessException("结束时间必须晚于开始时间");
        }
        User owner = currentUser();
        Shop shop = shopMapper.findByOwnerId(owner.getId());
        Product product = productMapper.findById(req.getProductId());
        if (shop == null || product == null || product.getShopId() == null
                || !shop.getId().equals(product.getShopId())) {
            throw new BusinessException(403, "只能为自己店铺的商品创建秒杀");
        }
        if (!"OPEN".equals(shop.getStatus())) {
            throw new BusinessException("店铺未营业");
        }
        int limit = req.getPerUserLimit() == null ? 1 : req.getPerUserLimit();
        int updated = productMapper.decreaseStock(req.getProductId(), req.getStock());
        if (updated == 0) {
            throw new BusinessException("可售库存不足，无法划出秒杀库存");
        }
        SeckillActivity activity = new SeckillActivity();
        activity.setProductId(req.getProductId());
        activity.setSeckillPriceCent(req.getSeckillPriceCent());
        activity.setStock(req.getStock());
        activity.setStartAt(req.getStartAt());
        activity.setEndAt(req.getEndAt());
        activity.setPerUserLimit(limit);
        activity.setStatus("RUNNING");
        activity.setId(activityMapper.insert(activity));
        Long productId = req.getProductId();
        AfterCommit.run(() -> {
            inventory.arm(activity);
            productService.evictProductCache(productId);
        });
        meterRegistry.counter("mall.seckill.armed").increment();
        return toResponse(activity);
    }

    public SeckillAcceptResponse place(long activityId, SeckillOrderRequest req) {
        SeckillActivity activity = activityMapper.findById(activityId);
        if (activity == null) {
            throw new BusinessException(404, "秒杀活动不存在");
        }
        assertShopOpen(activity.getProductId());
        User user = currentUser();
        String idem = StringUtils.hasText(req.getIdempotentKey())
                ? req.getIdempotentKey()
                : UUID.randomUUID().toString();
        String token = UUID.randomUUID().toString();
        List<?> raw = inventory.accept(
                activityId,
                user.getId(),
                req.getQty(),
                token,
                idem,
                Instant.now().toEpochMilli());
        if (raw == null || raw.isEmpty()) {
            throw new BusinessException("秒杀排队失败，请重试");
        }
        int code = codeOf(raw.get(0));
        String returnedToken = raw.size() > 1 && raw.get(1) != null ? raw.get(1).toString() : token;
        if (code == 2) {
            return fromResult(activityId, user.getId(), returnedToken);
        }
        if (code != 1) {
            throw rejection(code);
        }
        meterRegistry.counter("mall.seckill.queued").increment();
        return SeckillAcceptResponse.builder()
                .activityId(activityId)
                .token(returnedToken)
                .status(SeckillInventory.QUEUED)
                .build();
    }

    public SeckillAcceptResponse result(String token) {
        User user = currentUser();
        String value = inventory.result(user.getId(), token);
        if (value == null) {
            throw new BusinessException(404, "秒杀结果不存在");
        }
        return parseResult(null, token, value);
    }

    private SeckillAcceptResponse fromResult(long activityId, long userId, String token) {
        String value = inventory.result(userId, token);
        if (value == null) {
            return SeckillAcceptResponse.builder()
                    .activityId(activityId)
                    .token(token)
                    .status(SeckillInventory.QUEUED)
                    .build();
        }
        return parseResult(activityId, token, value);
    }

    private SeckillAcceptResponse parseResult(Long activityId, String token, String value) {
        if (value.startsWith("ORDER:")) {
            return SeckillAcceptResponse.builder()
                    .activityId(activityId)
                    .token(token)
                    .status("SUCCESS")
                    .orderId(Long.valueOf(value.substring("ORDER:".length())))
                    .build();
        }
        return SeckillAcceptResponse.builder()
                .activityId(activityId)
                .token(token)
                .status(value)
                .build();
    }

    private static int codeOf(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private BusinessException rejection(int code) {
        return switch (code) {
            case -1 -> new BusinessException(409, "秒杀库存已光");
            case -2 -> new BusinessException(409, "超过每人限购数量");
            case -3 -> new BusinessException("秒杀尚未开始");
            case -4 -> new BusinessException("秒杀已结束");
            default -> new BusinessException(404, "秒杀活动不存在");
        };
    }

    private SeckillActivityResponse toResponse(SeckillActivity activity) {
        return SeckillActivityResponse.builder()
                .id(activity.getId())
                .productId(activity.getProductId())
                .seckillPriceCent(activity.getSeckillPriceCent())
                .stock(activity.getStock())
                .remaining(inventory.remaining(activity.getId()))
                .startAt(activity.getStartAt())
                .endAt(activity.getEndAt())
                .perUserLimit(activity.getPerUserLimit())
                .status(activity.getStatus())
                .build();
    }

    private void assertShopOpen(Long productId) {
        Product product = productMapper.findById(productId);
        if (product == null || product.getShopId() == null) {
            return;
        }
        Shop shop = shopMapper.findById(product.getShopId());
        if (shop == null || !"OPEN".equals(shop.getStatus())) {
            throw new BusinessException("店铺未营业");
        }
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        return user;
    }
}
