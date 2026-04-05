package com.mall.mapper;

import com.mall.domain.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductMapper {

    Product findById(@Param("id") Long id);

    List<Product> listOnSale(@Param("offset") int offset, @Param("limit") int limit);

    int countOnSale();

    int decreaseStock(@Param("id") Long id, @Param("qty") int qty);

    int insert(Product product);
}
