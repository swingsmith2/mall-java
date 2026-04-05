package com.mall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PageResponse<T> {
    private long total;
    private int page;
    private int size;
    private List<T> records;
}
