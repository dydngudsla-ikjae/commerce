package com.commerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(
        @Schema(description = "Variant ID", example = "10") @NotNull Long variantId,
        @Schema(description = "주문 수량", example = "2", minimum = "1") @Min(1) int quantity
) {
}