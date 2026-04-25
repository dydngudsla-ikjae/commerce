package com.commerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DecreaseStockRequest(
        @Schema(description = "차감할 수량 (1 이상)", example = "1") @NotNull @Min(1) Integer quantity
) {
}