package com.mall.mapper;

import com.mall.domain.Shop;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ShopMapper {

    Long insert(Shop shop);

    Shop findById(@Param("id") Long id);

    Shop findByOwnerId(@Param("ownerId") Long ownerId);

    List<Shop> listAll();

    int forceClose(@Param("id") Long id, @Param("reason") String reason);
}
