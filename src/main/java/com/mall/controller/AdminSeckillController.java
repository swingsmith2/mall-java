package com.mall.controller;

import com.mall.common.ApiResponse;
import com.mall.dto.SeckillActivityResponse;
import com.mall.dto.SeckillCreateRequest;
import com.mall.service.SeckillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/seckill/activities")
@RequiredArgsConstructor
public class AdminSeckillController {

    private final SeckillService seckillService;

    @PostMapping
    public ApiResponse<SeckillActivityResponse> create(@Valid @RequestBody SeckillCreateRequest req) {
        return ApiResponse.ok(seckillService.create(req));
    }
}
