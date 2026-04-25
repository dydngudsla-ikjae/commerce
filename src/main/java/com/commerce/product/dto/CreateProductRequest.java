package com.commerce.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProductRequest(
        @NotBlank String name,
        String description,
        @NotNull Long categoryId
) {
}