package com.commerce.product.dto;

import com.commerce.product.domain.ProductStatus;
import com.commerce.product.domain.VariantStatus;

import java.util.List;

public record ProductDetailResponse(
        Long id,
        String name,
        String description,
        Long categoryId,
        ProductStatus status,
        List<OptionDto> options,
        List<VariantDto> variants
) {
    public record OptionDto(
            String name,
            List<String> values
    ) {}

    public record VariantDto(
            Long id,
            long price,
            int stock,
            VariantStatus status,
            List<String> options
    ) {}
}