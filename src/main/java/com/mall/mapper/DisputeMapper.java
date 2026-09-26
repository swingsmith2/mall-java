package com.mall.mapper;

import com.mall.domain.Dispute;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DisputeMapper {

    Long insert(Dispute dispute);

    Dispute findById(@Param("id") Long id);

    Dispute findOpenByOrderId(@Param("orderId") Long orderId);

    List<Dispute> listByStatus(@Param("status") String status);

    int resolve(@Param("id") Long id,
                @Param("resolution") String resolution,
                @Param("note") String note,
                @Param("handlerId") Long handlerId);

    int countMerchantOrder(@Param("orderId") Long orderId, @Param("ownerId") Long ownerId);
}
