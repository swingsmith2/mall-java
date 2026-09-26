package com.mall.controller;

import com.mall.common.ApiResponse;
import com.mall.dto.AlertSnapshot;
import com.mall.service.AlertMonitor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ops")
@RequiredArgsConstructor
public class AdminOpsController {

    private final AlertMonitor alertMonitor;

    @GetMapping("/alerts")
    public ApiResponse<AlertSnapshot> alerts() {
        return ApiResponse.ok(alertMonitor.refresh());
    }
}
