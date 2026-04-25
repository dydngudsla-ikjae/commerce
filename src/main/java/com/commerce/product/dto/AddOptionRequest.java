package com.commerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AddOptionRequest(
        @Schema(description = "옵션 이름", example = "색상") @NotBlank String name,
        @Schema(description = "옵션값 목록", example = "[\"BLACK\", \"WHITE\"]") @NotEmpty List<String> values
) {
}