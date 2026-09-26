package com.mall.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ShopResponse {
    private Long id;
    private Long ownerId;
    private String name;
    private String description;
    private String status;
    private String forcedCloseReason;
}
