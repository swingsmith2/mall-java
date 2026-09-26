package com.mall.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MerchantSessionResponse {
    private String token;
    private String username;
    private String role;
    private Long shopId;
}
