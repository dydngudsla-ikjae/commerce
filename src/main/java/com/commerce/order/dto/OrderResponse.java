package com.commerce.order.dto;

import com.commerce.order.domain.Order;
import com.commerce.order.domain.OrderStatus;

import java.time.LocalDateTime;

public record OrderResponse(
        Long id,
        Long memberId,
        OrderStatus status,
        long totalAmount,
        LocalDateTime createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getMemberId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }
}