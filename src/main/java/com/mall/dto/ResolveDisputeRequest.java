package com.mall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResolveDisputeRequest {
    @NotBlank
    @Pattern(regexp = "REFUND|REJECT", message = "处理结果只能是 REFUND 或 REJECT")
    private String action;
    @NotBlank(message = "处理说明不能为空")
    @Size(max = 1000)
    private String note;
}
