package com.commerce.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AddOptionRequest(
        @NotBlank String name,
        @NotEmpty List<String> values
) {
}