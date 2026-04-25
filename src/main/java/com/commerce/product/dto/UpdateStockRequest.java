package com.commerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateStockRequest(
        @Schema(description = "재고 수량 (0 이상, 0이면 SOLD_OUT으로 자동 전환)", example = "50") @NotNull @Min(0) Integer stock
) {
}