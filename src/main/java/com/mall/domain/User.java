package com.mall.domain;

import lombok.Data;

import java.time.Instant;

@Data
public class User {
    private Long id;
    private String username;
    private String password;
    private String role;
    private Instant createdAt;
}
