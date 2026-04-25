package com.commerce.product.dto;

import com.commerce.product.domain.ProductStatus;

import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        String name,
        String description,
        Long categoryId,
        ProductStatus status,
        LocalDateTime createdAt
) {
}