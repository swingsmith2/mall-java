package com.mall.controller;

import com.mall.common.ApiResponse;
import com.mall.dto.ProductCreateRequest;
import com.mall.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody ProductCreateRequest req) {
        var p = productService.create(req);
        return ApiResponse.ok(p.getId());
    }
}
