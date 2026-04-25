package com.commerce.product.dto;

import java.util.List;

public record OptionResponse(
        Long id,
        String name,
        List<String> values
) {
}