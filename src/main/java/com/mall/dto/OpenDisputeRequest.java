package com.mall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OpenDisputeRequest {
    @NotBlank(message = "纠纷原因不能为空")
    @Size(max = 1000)
    private String reason;
}
