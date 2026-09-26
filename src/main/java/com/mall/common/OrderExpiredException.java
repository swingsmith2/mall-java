package com.mall.common;

/**
 * 支付回调发现订单已过期并完成关单后抛出。事务需要提交关单，因此不要按普通业务异常回滚。
 */
public class OrderExpiredException extends RuntimeException {
    public OrderExpiredException() {
        super("订单已超时关闭");
    }
}
