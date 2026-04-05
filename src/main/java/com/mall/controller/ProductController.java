package com.mall.controller;

import com.mall.common.ApiResponse;
import com.mall.common.BusinessException;
import com.mall.domain.Product;
import com.mall.dto.PageResponse;
import com.mall.dto.ProductSummaryResponse;
import com.mall.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ApiResponse<PageResponse<ProductSummaryResponse>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (size > 100) {
            size = 100;
        }
        long total = productService.countOnSale();
        var records = productService.listPage(page, size);
        return ApiResponse.ok(new PageResponse<>(total, page, size, records));
    }

    @GetMapping("/{id}")
    public ApiResponse<Product> detail(@PathVariable Long id) {
        Product p = productService.getOnSaleById(id);
        if (p == null) {
            throw new BusinessException(404, "商品不存在");
        }
        return ApiResponse.ok(p);
    }
}
