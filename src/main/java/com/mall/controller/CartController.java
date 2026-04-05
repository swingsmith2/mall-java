package com.mall.controller;

import com.mall.common.ApiResponse;
import com.mall.common.BusinessException;
import com.mall.dto.CartItemResponse;
import com.mall.mapper.UserMapper;
import com.mall.service.CartService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final UserMapper userMapper;

    @GetMapping
    public ApiResponse<List<CartItemResponse>> list() {
        return ApiResponse.ok(cartService.list(currentUserId()));
    }

    @PostMapping("/items")
    public ApiResponse<Void> add(@RequestBody AddReq req) {
        if (req.getProductId() == null || req.getQty() == null || req.getQty() < 1) {
            throw new BusinessException("参数错误");
        }
        cartService.addItem(currentUserId(), req.getProductId(), req.getQty());
        return ApiResponse.ok();
    }

    @DeleteMapping("/items/{productId}")
    public ApiResponse<Void> remove(@PathVariable Long productId) {
        cartService.removeLine(currentUserId(), productId);
        return ApiResponse.ok();
    }

    @DeleteMapping
    public ApiResponse<Void> clear() {
        cartService.clear(currentUserId());
        return ApiResponse.ok();
    }

    private Long currentUserId() {
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        var u = userMapper.findByUsername(name);
        if (u == null) {
            throw new BusinessException(401, "未登录");
        }
        return u.getId();
    }

    @Data
    public static class AddReq {
        private Long productId;
        private Integer qty;
    }
}
