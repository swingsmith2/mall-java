package com.mall.mapper;

import com.mall.domain.SeckillActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeckillActivityMapper {

    Long insert(SeckillActivity activity);

    SeckillActivity findById(@Param("id") Long id);

    List<SeckillActivity> listRunning();

    int markEnded(@Param("id") Long id);
}
