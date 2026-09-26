package com.mall.service;

import com.mall.common.BusinessException;
import com.mall.domain.Product;
import com.mall.domain.Shop;
import com.mall.domain.User;
import com.mall.dto.MerchantRegisterRequest;
import com.mall.dto.MerchantSessionResponse;
import com.mall.dto.ProductCreateRequest;
import com.mall.mapper.ShopMapper;
import com.mall.mapper.UserMapper;
import com.mall.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final UserMapper userMapper;
    private final ShopMapper shopMapper;
    private final ProductService productService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public MerchantSessionResponse register(MerchantRegisterRequest req) {
        if (userMapper.findByUsername(req.getUsername()) != null) {
            throw new BusinessException("用户名已存在");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole("MERCHANT");
        userMapper.insert(user);

        Shop shop = new Shop();
        shop.setOwnerId(user.getId());
        shop.setName(req.getShopName());
        shop.setDescription(req.getDescription());
        shop.setStatus("OPEN");
        shop.setId(shopMapper.insert(shop));

        String token = jwtService.createToken(user.getUsername(), user.getRole());
        return MerchantSessionResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole())
                .shopId(shop.getId())
                .build();
    }

    @Transactional
    public Long createProduct(ProductCreateRequest req) {
        User user = currentUser();
        Shop shop = shopMapper.findByOwnerId(user.getId());
        if (shop == null) {
            throw new BusinessException(404, "尚未开店");
        }
        if (!"OPEN".equals(shop.getStatus())) {
            throw new BusinessException("店铺已被关闭，不能上架商品");
        }
        Product product = productService.createForShop(req, shop.getId());
        return product.getId();
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        return user;
    }
}
