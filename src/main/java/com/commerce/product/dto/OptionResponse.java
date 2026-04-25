package com.commerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record OptionResponse(
        @Schema(description = "옵션 ID", example = "1") Long id,
        @Schema(description = "옵션 이름", example = "색상") String name,
        @Schema(description = "옵션값 목록", example = "[\"BLACK\", \"WHITE\"]") List<String> values
) {
}