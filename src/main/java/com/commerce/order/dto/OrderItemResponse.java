package com.commerce.order.dto;

import com.commerce.order.domain.OrderItem;

public record OrderItemResponse(
        Long id,
        Long variantId,
        String productName,
        long price,
        int quantity
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getVariantId(),
                item.getProductName(),
                item.getPrice(),
                item.getQuantity()
        );
    }
}