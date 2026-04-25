package com.commerce.product.dto;

import com.commerce.product.domain.VariantStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateVariantRequest(
        @NotNull @Min(0) Long price,
        @NotNull VariantStatus status
) {
}