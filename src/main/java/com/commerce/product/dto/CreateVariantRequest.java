package com.commerce.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateVariantRequest(
        @NotNull @Min(0) Long price,
        @NotNull @Min(0) Integer stock,
        List<Long> optionValueIds
) {
}