package com.commerce.product.dto;

import com.commerce.product.domain.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ProductResponse(
        @Schema(description = "상품 ID", example = "1") Long id,
        @Schema(description = "상품 이름", example = "화이트 티셔츠") String name,
        @Schema(description = "상품 설명", example = "부드러운 면 소재의 기본 티셔츠") String description,
        @Schema(description = "카테고리 ID", example = "2") Long categoryId,
        @Schema(description = "상품 상태", example = "ON_SALE") ProductStatus status,
        @Schema(description = "등록일시", example = "2024-01-15T09:00:00") LocalDateTime createdAt
) {
}