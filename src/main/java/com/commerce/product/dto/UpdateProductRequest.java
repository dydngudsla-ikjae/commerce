package com.commerce.product.dto;

import com.commerce.product.domain.ProductStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateProductRequest(
        @NotBlank String name,
        String description,
        @NotNull ProductStatus status,
        @NotNull Long categoryId
) {
}