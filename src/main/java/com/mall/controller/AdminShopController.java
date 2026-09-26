package com.mall.controller;

import com.mall.common.ApiResponse;
import com.mall.dto.ForceCloseRequest;
import com.mall.dto.ShopResponse;
import com.mall.service.ShopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/shops")
@RequiredArgsConstructor
public class AdminShopController {

    private final ShopService shopService;

    @GetMapping
    public ApiResponse<List<ShopResponse>> list() {
        return ApiResponse.ok(shopService.listAll());
    }

    @PostMapping("/{id}/force-close")
    public ApiResponse<ShopResponse> forceClose(@PathVariable Long id, @Valid @RequestBody ForceCloseRequest req) {
        return ApiResponse.ok(shopService.forceClose(id, req));
    }
}
