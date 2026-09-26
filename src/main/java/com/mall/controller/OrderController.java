package com.mall.controller;

import com.mall.common.ApiResponse;
import com.mall.dto.DisputeResponse;
import com.mall.dto.OpenDisputeRequest;
import com.mall.dto.OrderResponse;
import com.mall.dto.PlaceOrderRequest;
import com.mall.dto.SimulatedPayRequest;
import com.mall.dto.SimulatedPayResponse;
import com.mall.service.DisputeService;
import com.mall.service.OrderService;
import com.mall.service.SimulatedPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final SimulatedPaymentService simulatedPaymentService;
    private final DisputeService disputeService;

    @PostMapping
    public ApiResponse<OrderResponse> place(@Valid @RequestBody PlaceOrderRequest req) {
        return ApiResponse.ok(orderService.placeOrder(req));
    }

    @PostMapping("/{id}/pay")
    public ApiResponse<SimulatedPayResponse> pay(@PathVariable Long id, @Valid @RequestBody SimulatedPayRequest req) {
        return ApiResponse.ok(simulatedPaymentService.pay(id, req.getChannel()));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable Long id) {
        return ApiResponse.ok(orderService.cancel(id));
    }

    @PostMapping("/{id}/disputes")
    public ApiResponse<DisputeResponse> dispute(@PathVariable Long id, @Valid @RequestBody OpenDisputeRequest req) {
        return ApiResponse.ok(disputeService.open(id, req));
    }

    @GetMapping("/mine")
    public ApiResponse<List<OrderResponse>> mine(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(orderService.myOrders(limit));
    }
}
