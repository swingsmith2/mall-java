package com.mall.controller;

import com.mall.common.ApiResponse;
import com.mall.dto.MerchantRegisterRequest;
import com.mall.dto.MerchantSessionResponse;
import com.mall.dto.ProductCreateRequest;
import com.mall.dto.SeckillActivityResponse;
import com.mall.dto.SeckillCreateRequest;
import com.mall.dto.ShopResponse;
import com.mall.service.MerchantService;
import com.mall.service.SeckillService;
import com.mall.service.ShopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;
    private final ShopService shopService;
    private final SeckillService seckillService;

    @PostMapping("/register")
    public ApiResponse<MerchantSessionResponse> register(@Valid @RequestBody MerchantRegisterRequest req) {
        return ApiResponse.ok(merchantService.register(req));
    }

    @GetMapping("/shop")
    public ApiResponse<ShopResponse> shop() {
        return ApiResponse.ok(shopService.myShop());
    }

    @PostMapping("/products")
    public ApiResponse<Long> createProduct(@Valid @RequestBody ProductCreateRequest req) {
        return ApiResponse.ok(merchantService.createProduct(req));
    }

    @PostMapping("/seckill/activities")
    public ApiResponse<SeckillActivityResponse> createSeckill(@Valid @RequestBody SeckillCreateRequest req) {
        return ApiResponse.ok(seckillService.create(req));
    }
}
