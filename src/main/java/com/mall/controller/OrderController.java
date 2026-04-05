package com.mall.controller;

import com.mall.common.ApiResponse;
import com.mall.dto.OrderResponse;
import com.mall.dto.PlaceOrderRequest;
import com.mall.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ApiResponse<OrderResponse> place(@Valid @RequestBody PlaceOrderRequest req) {
        return ApiResponse.ok(orderService.placeOrder(req));
    }

    @GetMapping("/mine")
    public ApiResponse<List<OrderResponse>> mine(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(orderService.myOrders(limit));
    }
}
