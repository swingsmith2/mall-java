package com.mall.controller;

import com.mall.common.ApiResponse;
import com.mall.dto.DisputeResponse;
import com.mall.dto.ResolveDisputeRequest;
import com.mall.service.DisputeService;
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
@RequestMapping("/api/cs/disputes")
@RequiredArgsConstructor
public class CsDisputeController {

    private final DisputeService disputeService;

    @GetMapping
    public ApiResponse<List<DisputeResponse>> list(@RequestParam(defaultValue = "OPEN") String status) {
        return ApiResponse.ok(disputeService.list(status));
    }

    @PostMapping("/{id}/resolve")
    public ApiResponse<DisputeResponse> resolve(@PathVariable Long id, @Valid @RequestBody ResolveDisputeRequest req) {
        return ApiResponse.ok(disputeService.resolve(id, req));
    }
}
