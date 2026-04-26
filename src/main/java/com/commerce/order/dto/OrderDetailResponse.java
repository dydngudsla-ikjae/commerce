package com.commerce.order.dto;

import com.commerce.order.domain.Order;
import com.commerce.order.domain.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResponse(
        Long id,
        Long memberId,
        OrderStatus status,
        long totalAmount,
        List<OrderItemResponse> items,
        LocalDateTime createdAt
) {
    public static OrderDetailResponse from(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(OrderItemResponse::from)
                .toList();
        return new OrderDetailResponse(
                order.getId(),
                order.getMemberId(),
                order.getStatus(),
                order.getTotalAmount(),
                items,
                order.getCreatedAt()
        );
    }
}