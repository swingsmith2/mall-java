package com.mall.mapper;

import com.mall.domain.Order;
import com.mall.domain.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface OrderMapper {

    Long insertOrder(Order order);

    Long insertOrderIgnoreConflict(Order order);

    int insertItem(OrderItem item);

    List<Order> listByUser(@Param("userId") Long userId, @Param("limit") int limit);

    Order findById(@Param("id") Long id);

    Order findByIdForUpdate(@Param("id") Long id);

    Order findByUserAndIdempotentKey(@Param("userId") Long userId, @Param("key") String key);

    List<OrderItem> listItemsByOrderId(@Param("orderId") Long orderId);

    int markPaid(@Param("id") Long id);

    int markCancelled(@Param("id") Long id);

    int sumActiveSeckillQty(@Param("activityId") Long activityId);

    List<Long> listUnpaidIdsCreatedBefore(@Param("deadline") Instant deadline, @Param("limit") int limit);
}
