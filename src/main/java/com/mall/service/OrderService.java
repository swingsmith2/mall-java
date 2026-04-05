package com.mall.service;

import com.mall.common.BusinessException;
import com.mall.domain.Order;
import com.mall.domain.OrderItem;
import com.mall.domain.Product;
import com.mall.domain.User;
import com.mall.dto.OrderResponse;
import com.mall.dto.PlaceOrderRequest;
import com.mall.mapper.OrderMapper;
import com.mall.mapper.ProductMapper;
import com.mall.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final ProductService productService;
    private final RedisRateLimiter rateLimiter;
    private final OrderNotificationService notificationService;

    public List<OrderResponse> myOrders(int limit) {
        User u = currentUser();
        List<Order> orders = orderMapper.listByUser(u.getId(), Math.min(limit, 100));
        List<OrderResponse> out = new ArrayList<>();
        for (Order o : orders) {
            out.add(toResponse(o, orderMapper.listItemsByOrderId(o.getId())));
        }
        return out;
    }

    /**
     * 串行化隔离避免同一幂等键并发双单；高并发场景可改为分库分表 + 业务幂等表或最终一致消息。
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public OrderResponse placeOrder(PlaceOrderRequest req) {
        User u = currentUser();
        rateLimiter.checkOrderLimit(String.valueOf(u.getId()));

        if (StringUtils.hasText(req.getIdempotentKey())) {
            Order exist = orderMapper.findByUserAndIdempotentKey(u.getId(), req.getIdempotentKey());
            if (exist != null) {
                return toResponse(exist, orderMapper.listItemsByOrderId(exist.getId()));
            }
        }

        long total = 0;
        List<LineSnap> snaps = new ArrayList<>();
        for (var line : req.getItems()) {
            Product p = productMapper.findById(line.getProductId());
            if (p == null || p.getStatus() == null || p.getStatus() != 1) {
                throw new BusinessException("商品不存在或已下架: " + line.getProductId());
            }
            if (p.getStock() < line.getQty()) {
                throw new BusinessException("库存不足: " + p.getName());
            }
            total += p.getPriceCent() * line.getQty();
            snaps.add(new LineSnap(p.getId(), line.getQty(), p.getPriceCent()));
        }

        for (LineSnap s : snaps) {
            int updated = productMapper.decreaseStock(s.productId, s.qty);
            if (updated == 0) {
                throw new BusinessException("抢购人数过多，库存不足，请重试");
            }
        }

        Order order = new Order();
        order.setUserId(u.getId());
        order.setTotalCent(total);
        order.setStatus("PAID");
        order.setIdempotentKey(StringUtils.hasText(req.getIdempotentKey()) ? req.getIdempotentKey() : null);
        try {
            orderMapper.insertOrder(order);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            if (StringUtils.hasText(req.getIdempotentKey())) {
                Order exist = orderMapper.findByUserAndIdempotentKey(u.getId(), req.getIdempotentKey());
                if (exist != null) {
                    return toResponse(exist, orderMapper.listItemsByOrderId(exist.getId()));
                }
            }
            throw new BusinessException("订单创建失败，请重试");
        }

        List<OrderItem> persisted = new ArrayList<>();
        for (LineSnap s : snaps) {
            OrderItem it = new OrderItem();
            it.setOrderId(order.getId());
            it.setProductId(s.productId);
            it.setQty(s.qty);
            it.setPriceCent(s.priceCent);
            orderMapper.insertItem(it);
            persisted.add(it);
            productService.evictProductCache(s.productId);
        }

        notificationService.onOrderCreated(order.getId(), u.getId(), order.getTotalCent());
        return toResponse(order, persisted);
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User u = userMapper.findByUsername(username);
        if (u == null) {
            throw new BusinessException(401, "未登录");
        }
        return u;
    }

    private static OrderResponse toResponse(Order o, List<OrderItem> items) {
        List<OrderResponse.OrderLineResponse> lines = items.stream()
                .map(it -> OrderResponse.OrderLineResponse.builder()
                        .productId(it.getProductId())
                        .qty(it.getQty())
                        .priceCent(it.getPriceCent())
                        .build())
                .toList();
        return OrderResponse.builder()
                .id(o.getId())
                .totalCent(o.getTotalCent())
                .status(o.getStatus())
                .createdAt(o.getCreatedAt())
                .lines(lines)
                .build();
    }

    private record LineSnap(Long productId, int qty, long priceCent) {}
}
