package com.commerce.product.dto;

import com.commerce.product.domain.VariantStatus;

import java.util.List;

public record VariantResponse(
        Long id,
        long price,
        int stock,
        VariantStatus status,
        List<String> options
) {
}