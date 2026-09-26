package com.mall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MerchantRegisterRequest {
    @NotBlank
    @Size(min = 3, max = 64)
    private String username;
    @NotBlank
    @Size(min = 6, max = 128)
    private String password;
    @NotBlank
    @Size(max = 128)
    private String shopName;
    @Size(max = 500)
    private String description;
}
