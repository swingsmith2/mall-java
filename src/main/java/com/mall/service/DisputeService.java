package com.mall.service;

import com.mall.common.BusinessException;
import com.mall.domain.Dispute;
import com.mall.domain.Order;
import com.mall.domain.OrderStatus;
import com.mall.domain.User;
import com.mall.dto.DisputeResponse;
import com.mall.dto.OpenDisputeRequest;
import com.mall.dto.ResolveDisputeRequest;
import com.mall.mapper.DisputeMapper;
import com.mall.mapper.OrderMapper;
import com.mall.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DisputeService {

    private final DisputeMapper disputeMapper;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final OrderService orderService;

    @Transactional
    public DisputeResponse open(Long orderId, OpenDisputeRequest req) {
        User user = currentUser();
        Order order = orderMapper.findById(orderId);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        boolean buyer = order.getUserId().equals(user.getId());
        boolean merchant = disputeMapper.countMerchantOrder(orderId, user.getId()) > 0;
        if (!buyer && !merchant) {
            throw new BusinessException(403, "只能对自己相关的订单发起纠纷");
        }
        if (!OrderStatus.PAID.name().equals(order.getStatus())) {
            throw new BusinessException(409, "只有已支付订单可以发起纠纷");
        }
        if (disputeMapper.findOpenByOrderId(orderId) != null) {
            throw new BusinessException(409, "该订单已有未处理纠纷");
        }
        Dispute dispute = new Dispute();
        dispute.setOrderId(orderId);
        dispute.setOpenerId(user.getId());
        dispute.setReason(req.getReason());
        dispute.setStatus("OPEN");
        dispute.setId(disputeMapper.insert(dispute));
        return toResponse(disputeMapper.findById(dispute.getId()));
    }

    public List<DisputeResponse> list(String status) {
        String filter = status == null || status.isBlank() ? "OPEN" : status;
        return disputeMapper.listByStatus(filter).stream().map(this::toResponse).toList();
    }

    @Transactional
    public DisputeResponse resolve(Long disputeId, ResolveDisputeRequest req) {
        User handler = currentUser();
        Dispute dispute = disputeMapper.findById(disputeId);
        if (dispute == null) {
            throw new BusinessException(404, "纠纷不存在");
        }
        if (!"OPEN".equals(dispute.getStatus())) {
            throw new BusinessException(409, "纠纷已处理");
        }
        if ("REFUND".equals(req.getAction())) {
            orderService.refundPaid(dispute.getOrderId());
        }
        int updated = disputeMapper.resolve(disputeId, req.getAction(), req.getNote(), handler.getId());
        if (updated == 0) {
            throw new BusinessException(409, "纠纷状态已变化");
        }
        return toResponse(disputeMapper.findById(disputeId));
    }

    private DisputeResponse toResponse(Dispute dispute) {
        return DisputeResponse.builder()
                .id(dispute.getId())
                .orderId(dispute.getOrderId())
                .openerId(dispute.getOpenerId())
                .reason(dispute.getReason())
                .status(dispute.getStatus())
                .resolution(dispute.getResolution())
                .resolutionNote(dispute.getResolutionNote())
                .handlerId(dispute.getHandlerId())
                .createdAt(dispute.getCreatedAt())
                .resolvedAt(dispute.getResolvedAt())
                .build();
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
