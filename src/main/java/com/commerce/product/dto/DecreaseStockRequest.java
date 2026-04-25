package com.commerce.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DecreaseStockRequest(
        @NotNull @Min(1) Integer quantity
) {
}