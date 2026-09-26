package com.mall.service;

import com.mall.common.AfterCommit;
import com.mall.common.BusinessException;
import com.mall.common.OrderExpiredException;
import com.mall.config.MallProperties;
import com.mall.domain.Order;
import com.mall.domain.OrderItem;
import com.mall.domain.OrderStatus;
import com.mall.domain.Payment;
import com.mall.domain.Product;
import com.mall.domain.SeckillActivity;
import com.mall.domain.Shop;
import com.mall.domain.SeckillQueueMessage;
import com.mall.domain.User;
import com.mall.dto.OrderResponse;
import com.mall.dto.PaymentCallbackRequest;
import com.mall.dto.PlaceOrderRequest;
import com.mall.mapper.OrderMapper;
import com.mall.mapper.PaymentMapper;
import com.mall.mapper.ProductMapper;
import com.mall.mapper.SeckillActivityMapper;
import com.mall.mapper.ShopMapper;
import com.mall.mapper.UserMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final ShopMapper shopMapper;
    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final ProductService productService;
    private final RedisRateLimiter rateLimiter;
    private final OutboxService outboxService;
    private final SeckillInventory seckillInventory;
    private final SeckillActivityMapper seckillActivityMapper;
    private final MallProperties mallProperties;
    private final MeterRegistry meterRegistry;

    public List<OrderResponse> myOrders(int limit) {
        User u = currentUser();
        List<Order> orders = orderMapper.listByUser(u.getId(), Math.min(limit, 100));
        List<OrderResponse> out = new ArrayList<>();
        for (Order o : orders) {
            out.add(toResponse(o, orderMapper.listItemsByOrderId(o.getId())));
        }
        return out;
    }

    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest req) {
        User u = currentUser();
        if (StringUtils.hasText(req.getIdempotentKey())) {
            Order exist = orderMapper.findByUserAndIdempotentKey(u.getId(), req.getIdempotentKey());
            if (exist != null) {
                return toResponse(exist, orderMapper.listItemsByOrderId(exist.getId()));
            }
        }
        rateLimiter.checkOrderLimit(String.valueOf(u.getId()));

        long total = 0;
        List<LineSnap> snaps = new ArrayList<>();
        for (var line : req.getItems()) {
            Product p = productMapper.findById(line.getProductId());
            if (p == null || p.getStatus() == null || p.getStatus() != 1) {
                throw new BusinessException("商品不存在或已下架: " + line.getProductId());
            }
            if (p.getShopId() != null) {
                Shop shop = shopMapper.findById(p.getShopId());
                if (shop == null || !"OPEN".equals(shop.getStatus())) {
                    throw new BusinessException("店铺未营业");
                }
            }
            if (p.getStock() < line.getQty()) {
                throw new BusinessException("库存不足: " + p.getName());
            }
            total += p.getPriceCent() * line.getQty();
            snaps.add(new LineSnap(p.getId(), line.getQty(), p.getPriceCent()));
        }

        Order order = new Order();
        order.setUserId(u.getId());
        order.setTotalCent(total);
        order.setStatus(OrderStatus.CREATED.name());
        order.setIdempotentKey(StringUtils.hasText(req.getIdempotentKey()) ? req.getIdempotentKey() : null);

        Long id;
        if (StringUtils.hasText(req.getIdempotentKey())) {
            id = orderMapper.insertOrderIgnoreConflict(order);
            if (id == null) {
                Order exist = orderMapper.findByUserAndIdempotentKey(u.getId(), req.getIdempotentKey());
                if (exist == null) {
                    throw new BusinessException("订单创建失败，请重试");
                }
                return toResponse(exist, orderMapper.listItemsByOrderId(exist.getId()));
            }
        } else {
            id = orderMapper.insertOrder(order);
        }
        order.setId(id);

        List<OrderItem> persisted = new ArrayList<>();
        for (LineSnap s : snaps) {
            int updated = productMapper.decreaseStock(s.productId, s.qty);
            if (updated == 0) {
                throw new BusinessException("抢购人数过多，库存不足，请重试");
            }
            OrderItem it = new OrderItem();
            it.setOrderId(order.getId());
            it.setProductId(s.productId);
            it.setQty(s.qty);
            it.setPriceCent(s.priceCent);
            orderMapper.insertItem(it);
            persisted.add(it);
            Long productId = s.productId;
            AfterCommit.run(() -> productService.evictProductCache(productId));
        }

        outboxService.enqueue("ORDER_CREATED", order.getId(), payload(order, u.getId(), "PLACE"));
        AfterCommit.run(() -> meterRegistry.counter("mall.order.placed").increment());
        return toResponse(orderMapper.findById(order.getId()), persisted);
    }

    @Transactional(noRollbackFor = OrderExpiredException.class)
    public OrderResponse applyPayment(PaymentCallbackRequest req) {
        Order order = orderMapper.findByIdForUpdate(req.getOrderId());
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        if (!order.getTotalCent().equals(req.getAmountCent())) {
            throw new BusinessException("支付金额与订单不一致");
        }

        Payment existing = paymentMapper.findByPaymentNo(req.getPaymentNo());
        if (existing != null) {
            if (!existing.getOrderId().equals(order.getId())) {
                throw new BusinessException("支付单号已用于其他订单");
            }
            Order fresh = orderMapper.findById(order.getId());
            return toResponse(fresh, orderMapper.listItemsByOrderId(fresh.getId()));
        }

        if (OrderStatus.PAID.name().equals(order.getStatus())) {
            throw new BusinessException(409, "订单已支付");
        }
        if (OrderStatus.CANCELLED.name().equals(order.getStatus())) {
            throw new BusinessException(409, "订单已关闭，请原路退款");
        }
        if (!payDeadline(order).isAfter(Instant.now())) {
            restoreAndCancel(order, "TIMEOUT");
            throw new OrderExpiredException();
        }

        int updated = orderMapper.markPaid(order.getId());
        if (updated == 0) {
            throw new BusinessException(409, "订单状态已变化");
        }

        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setPaymentNo(req.getPaymentNo());
        payment.setAmountCent(req.getAmountCent());
        payment.setStatus("SUCCESS");
        paymentMapper.insert(payment);

        order.setStatus(OrderStatus.PAID.name());
        outboxService.enqueue("ORDER_PAID", order.getId(), payload(order, order.getUserId(), "PAY"));
        AfterCommit.run(() -> meterRegistry.counter("mall.order.paid").increment());
        return toResponse(orderMapper.findById(order.getId()), orderMapper.listItemsByOrderId(order.getId()));
    }

    @Transactional
    public OrderResponse cancel(Long orderId) {
        User u = currentUser();
        Order order = orderMapper.findByIdForUpdate(orderId);
        if (order == null || !order.getUserId().equals(u.getId())) {
            throw new BusinessException(404, "订单不存在");
        }
        if (!OrderStatus.CREATED.name().equals(order.getStatus())) {
            throw new BusinessException(409, "订单当前状态不可取消");
        }
        restoreAndCancel(order, "USER");
        return toResponse(orderMapper.findById(orderId), orderMapper.listItemsByOrderId(orderId));
    }

    /**
     * 超时扫描调用。只有仍处于 CREATED 且已过支付截止时间的订单会关单并回补库存。
     */
    @Transactional
    public boolean cancelIfUnpaid(Long orderId) {
        Order order = orderMapper.findByIdForUpdate(orderId);
        if (order == null || !OrderStatus.CREATED.name().equals(order.getStatus())) {
            return false;
        }
        if (payDeadline(order).isAfter(Instant.now())) {
            return false;
        }
        restoreAndCancel(order, "TIMEOUT");
        return true;
    }

    public Order requireOwned(Long orderId) {
        User user = currentUser();
        Order order = orderMapper.findById(orderId);
        if (order == null || !order.getUserId().equals(user.getId())) {
            throw new BusinessException(404, "订单不存在");
        }
        return order;
    }

    @Transactional
    public OrderResponse refundPaid(Long orderId) {
        Order order = orderMapper.findByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        if (!OrderStatus.PAID.name().equals(order.getStatus())) {
            throw new BusinessException(409, "只有已支付订单可以退款");
        }
        int updated = orderMapper.markRefunded(order.getId());
        if (updated == 0) {
            throw new BusinessException(409, "订单状态已变化");
        }
        restoreStock(order);
        order.setStatus(OrderStatus.REFUNDED.name());
        outboxService.enqueue("ORDER_REFUNDED", order.getId(), payload(order, order.getUserId(), "REFUND"));
        AfterCommit.run(() -> meterRegistry.counter("mall.order.refunded").increment());
        return toResponse(orderMapper.findById(orderId), orderMapper.listItemsByOrderId(orderId));
    }

    private void restoreAndCancel(Order order, String reason) {
        int updated = orderMapper.markCancelled(order.getId());
        if (updated == 0) {
            throw new BusinessException(409, "订单状态已变化");
        }
        restoreStock(order);
        order.setStatus(OrderStatus.CANCELLED.name());
        outboxService.enqueue("ORDER_CANCELLED", order.getId(), payload(order, order.getUserId(), reason));
        AfterCommit.run(() -> meterRegistry.counter("mall.order.cancelled").increment());
    }

    private void restoreStock(Order order) {
        boolean seckillOpen = seckillStillRunning(order);
        for (OrderItem item : orderMapper.listItemsByOrderId(order.getId())) {
            if (seckillOpen) {
                long activityId = order.getSeckillActivityId();
                long userId = order.getUserId();
                int qty = item.getQty();
                Long productId = item.getProductId();
                AfterCommit.run(() -> {
                    if (!seckillInventory.restoreToRedis(activityId, userId, qty)) {
                        productMapper.increaseStock(productId, qty);
                        productService.evictProductCache(productId);
                    }
                });
            } else {
                productMapper.increaseStock(item.getProductId(), item.getQty());
                Long productId = item.getProductId();
                AfterCommit.run(() -> productService.evictProductCache(productId));
            }
        }
    }

    @Transactional
    public Long placeSeckillOrder(SeckillQueueMessage message) {
        String key = "sk:" + message.getActivityId() + ":" + message.getToken();
        Order existing = orderMapper.findByUserAndIdempotentKey(message.getUserId(), key);
        if (existing != null) {
            return existing.getId();
        }
        Order order = new Order();
        order.setUserId(message.getUserId());
        order.setTotalCent(message.getPriceCent() * message.getQty());
        order.setStatus(OrderStatus.CREATED.name());
        order.setIdempotentKey(key);
        order.setSeckillActivityId(message.getActivityId());
        Long id = orderMapper.insertOrderIgnoreConflict(order);
        if (id == null) {
            Order raced = orderMapper.findByUserAndIdempotentKey(message.getUserId(), key);
            if (raced == null) {
                throw new BusinessException("秒杀订单创建失败，请重试");
            }
            return raced.getId();
        }
        order.setId(id);
        OrderItem item = new OrderItem();
        item.setOrderId(id);
        item.setProductId(message.getProductId());
        item.setQty(message.getQty());
        item.setPriceCent(message.getPriceCent());
        orderMapper.insertItem(item);
        outboxService.enqueue("ORDER_CREATED", id, payload(order, message.getUserId(), "SECKILL"));
        AfterCommit.run(() -> meterRegistry.counter("mall.order.placed").increment());
        return id;
    }

    @Transactional
    public void returnSeckillStockToProduct(Long productId, int qty) {
        productMapper.increaseStock(productId, qty);
        AfterCommit.run(() -> productService.evictProductCache(productId));
    }

    private boolean seckillStillRunning(Order order) {
        if (order.getSeckillActivityId() == null) {
            return false;
        }
        SeckillActivity activity = seckillActivityMapper.findById(order.getSeckillActivityId());
        return activity != null && "RUNNING".equals(activity.getStatus());
    }

    private Map<String, Object> payload(Order order, Long userId, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", order.getId());
        body.put("userId", userId);
        body.put("totalCent", order.getTotalCent());
        body.put("status", order.getStatus());
        body.put("reason", reason);
        return body;
    }

    private Instant payDeadline(Order order) {
        return order.getCreatedAt().plus(mallProperties.getOrder().getPayTimeout());
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User u = userMapper.findByUsername(username);
        if (u == null) {
            throw new BusinessException(401, "未登录");
        }
        return u;
    }

    private OrderResponse toResponse(Order o, List<OrderItem> items) {
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
                .paidAt(o.getPaidAt())
                .cancelledAt(o.getCancelledAt())
                .payDeadline(o.getCreatedAt() == null ? null : payDeadline(o))
                .lines(lines)
                .build();
    }

    private record LineSnap(Long productId, int qty, long priceCent) {}
}
