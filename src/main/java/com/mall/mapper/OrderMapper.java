package com.mall.mapper;

import com.mall.domain.Order;
import com.mall.domain.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderMapper {

    int insertOrder(Order order);

    int insertItem(OrderItem item);

    List<Order> listByUser(@Param("userId") Long userId, @Param("limit") int limit);

    Order findByUserAndIdempotentKey(@Param("userId") Long userId, @Param("key") String key);

    List<OrderItem> listItemsByOrderId(@Param("orderId") Long orderId);
}
