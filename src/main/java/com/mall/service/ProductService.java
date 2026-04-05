package com.mall.service;

import com.mall.domain.Product;
import com.mall.dto.ProductCreateRequest;
import com.mall.dto.ProductSummaryResponse;
import com.mall.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;

    @Cacheable(value = "products", key = "#id", unless = "#result == null || #result.status != 1")
    public Product getOnSaleById(Long id) {
        Product p = productMapper.findById(id);
        if (p == null || p.getStatus() == null || p.getStatus() != 1) {
            return null;
        }
        return p;
    }

    public List<ProductSummaryResponse> listPage(int page, int size) {
        int offset = Math.max(0, (page - 1) * size);
        return productMapper.listOnSale(offset, size).stream()
                .map(p -> ProductSummaryResponse.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .priceCent(p.getPriceCent())
                        .stock(p.getStock())
                        .build())
                .toList();
    }

    public long countOnSale() {
        return productMapper.countOnSale();
    }

    @Transactional
    @CacheEvict(value = "products", key = "#result.id")
    public Product create(ProductCreateRequest req) {
        Product p = new Product();
        p.setCategoryId(req.getCategoryId());
        p.setName(req.getName());
        p.setDescription(req.getDescription());
        p.setPriceCent(req.getPriceCent());
        p.setStock(req.getStock());
        p.setStatus(1);
        productMapper.insert(p);
        return p;
    }

    @CacheEvict(value = "products", key = "#id")
    public void evictProductCache(Long id) {
        // 注解驱动清理缓存
    }
}
