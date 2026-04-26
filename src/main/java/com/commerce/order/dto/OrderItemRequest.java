package com.commerce.order.dto;

public record OrderItemRequest(
        Long variantId,
        int quantity
) {
}