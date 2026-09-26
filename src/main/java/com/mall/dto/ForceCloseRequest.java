package com.mall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ForceCloseRequest {
    @NotBlank(message = "关闭原因不能为空")
    @Size(max = 500)
    private String reason;
}
