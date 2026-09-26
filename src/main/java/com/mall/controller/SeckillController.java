package com.mall.controller;

import com.mall.common.ApiResponse;
import com.mall.dto.SeckillAcceptResponse;
import com.mall.dto.SeckillActivityResponse;
import com.mall.dto.SeckillOrderRequest;
import com.mall.service.SeckillService;
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
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    @GetMapping("/activities")
    public ApiResponse<List<SeckillActivityResponse>> list() {
        return ApiResponse.ok(seckillService.listOpen());
    }

    @GetMapping("/activities/{id}")
    public ApiResponse<SeckillActivityResponse> get(@PathVariable long id) {
        return ApiResponse.ok(seckillService.get(id));
    }

    @PostMapping("/activities/{id}/orders")
    public ApiResponse<SeckillAcceptResponse> place(@PathVariable long id, @Valid @RequestBody SeckillOrderRequest req) {
        return ApiResponse.ok(seckillService.place(id, req));
    }

    @GetMapping("/orders/{token}")
    public ApiResponse<SeckillAcceptResponse> result(@PathVariable String token) {
        return ApiResponse.ok(seckillService.result(token));
    }
}
