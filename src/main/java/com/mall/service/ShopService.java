package com.mall.service;

import com.mall.common.AfterCommit;
import com.mall.common.BusinessException;
import com.mall.domain.Shop;
import com.mall.domain.User;
import com.mall.dto.ForceCloseRequest;
import com.mall.dto.ShopResponse;
import com.mall.mapper.ProductMapper;
import com.mall.mapper.ShopMapper;
import com.mall.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopMapper shopMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final ProductService productService;

    public ShopResponse myShop() {
        User user = currentUser();
        Shop shop = shopMapper.findByOwnerId(user.getId());
        if (shop == null) {
            throw new BusinessException(404, "尚未开店");
        }
        return toResponse(shop);
    }

    public List<ShopResponse> listAll() {
        return shopMapper.listAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public ShopResponse forceClose(Long shopId, ForceCloseRequest req) {
        Shop shop = shopMapper.findById(shopId);
        if (shop == null) {
            throw new BusinessException(404, "店铺不存在");
        }
        if ("FORCED_CLOSED".equals(shop.getStatus())) {
            return toResponse(shop);
        }
        int updated = shopMapper.forceClose(shopId, req.getReason());
        if (updated == 0) {
            throw new BusinessException(409, "店铺状态已变化");
        }
        for (Long productId : productMapper.listIdsByShop(shopId)) {
            AfterCommit.run(() -> productService.evictProductCache(productId));
        }
        shop.setStatus("FORCED_CLOSED");
        shop.setForcedCloseReason(req.getReason());
        return toResponse(shop);
    }

    public boolean isOpenShop(Long shopId) {
        if (shopId == null) {
            return true;
        }
        Shop shop = shopMapper.findById(shopId);
        return shop != null && "OPEN".equals(shop.getStatus());
    }

    private ShopResponse toResponse(Shop shop) {
        return ShopResponse.builder()
                .id(shop.getId())
                .ownerId(shop.getOwnerId())
                .name(shop.getName())
                .description(shop.getDescription())
                .status(shop.getStatus())
                .forcedCloseReason(shop.getForcedCloseReason())
                .build();
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
