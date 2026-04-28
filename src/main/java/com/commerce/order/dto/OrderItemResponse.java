package com.commerce.order.dto;

import com.commerce.order.domain.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;

public record OrderItemResponse(
        @Schema(description = "주문 항목 ID", example = "5001") Long id,
        @Schema(description = "Variant ID", example = "10") Long variantId,
        @Schema(description = "주문 시점 상품명", example = "티셔츠") String productName,
        @Schema(description = "주문 시점 단가 (원)", example = "29500") long price,
        @Schema(description = "주문 수량", example = "2") int quantity
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