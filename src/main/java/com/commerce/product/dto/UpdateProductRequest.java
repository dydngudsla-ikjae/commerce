package com.commerce.product.dto;

import com.commerce.product.domain.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateProductRequest(
        @Schema(description = "상품 이름", example = "화이트 티셔츠") @NotBlank String name,
        @Schema(description = "상품 설명", example = "부드러운 면 소재의 기본 티셔츠") String description,
        @Schema(description = "상품 이미지 URL", example = "https://cdn.example.com/images/white-tshirt.jpg") String imageUrl,
        @Schema(description = "상품 상태", example = "ON_SALE") @NotNull ProductStatus status,
        @Schema(description = "카테고리 ID", example = "2") @NotNull Long categoryId
) {
}