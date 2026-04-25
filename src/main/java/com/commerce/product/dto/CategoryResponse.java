package com.commerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record CategoryResponse(
        @Schema(description = "카테고리 ID", example = "1") Long id,
        @Schema(description = "카테고리 이름", example = "의류") String name,
        @Schema(description = "하위 카테고리 목록") List<CategoryResponse> children
) {
}