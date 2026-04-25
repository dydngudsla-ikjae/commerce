package com.commerce.product.dto;

import com.commerce.product.domain.VariantStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateVariantRequest(
        @Schema(description = "가격 (원, 0 이상)", example = "29000") @NotNull @Min(0) Long price,
        @Schema(description = "Variant 상태", example = "ON_SALE") @NotNull VariantStatus status
) {
}