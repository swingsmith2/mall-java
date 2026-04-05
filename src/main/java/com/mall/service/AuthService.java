package com.mall.service;

import com.mall.common.BusinessException;
import com.mall.domain.User;
import com.mall.dto.LoginRequest;
import com.mall.dto.RegisterRequest;
import com.mall.dto.TokenResponse;
import com.mall.mapper.UserMapper;
import com.mall.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public void register(RegisterRequest req) {
        if (userMapper.findByUsername(req.getUsername()) != null) {
            throw new BusinessException("用户名已存在");
        }
        User u = new User();
        u.setUsername(req.getUsername());
        u.setPassword(passwordEncoder.encode(req.getPassword()));
        u.setRole("USER");
        userMapper.insert(u);
    }

    public TokenResponse login(LoginRequest req) {
        User u = userMapper.findByUsername(req.getUsername());
        if (u == null || !passwordEncoder.matches(req.getPassword(), u.getPassword())) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        String token = jwtService.createToken(u.getUsername(), u.getRole());
        return new TokenResponse(token, u.getUsername(), u.getRole());
    }
}
