package com.mall.domain;

import lombok.Data;

@Data
public class Category {
    private Long id;
    private String name;
    private Long parentId;
    private Integer sortOrder;
}
