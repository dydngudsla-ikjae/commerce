package com.commerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateCategoryRequest(
        @Schema(description = "카테고리 이름", example = "의류") @NotBlank String name,
        @Schema(description = "부모 카테고리 ID (없으면 루트 카테고리로 생성)", example = "1") Long parentId
) {
}