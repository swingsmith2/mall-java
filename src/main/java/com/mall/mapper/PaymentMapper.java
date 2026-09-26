package com.mall.mapper;

import com.mall.domain.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentMapper {

    Payment findByPaymentNo(@Param("paymentNo") String paymentNo);

    int insert(Payment payment);
}
