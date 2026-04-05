package com.mall.domain;

import lombok.Data;

import java.time.Instant;

@Data
public class Product {
    private Long id;
    private Long categoryId;
    private String name;
    private String description;
    private Long priceCent;
    private Integer stock;
    private Integer status;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
}
