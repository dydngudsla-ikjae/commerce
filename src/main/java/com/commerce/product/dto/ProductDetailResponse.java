package com.commerce.product.dto;

import com.commerce.product.domain.ProductStatus;
import com.commerce.product.domain.VariantStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ProductDetailResponse(
        @Schema(description = "상품 ID", example = "1") Long id,
        @Schema(description = "상품 이름", example = "화이트 티셔츠") String name,
        @Schema(description = "상품 설명", example = "부드러운 면 소재의 기본 티셔츠") String description,
        @Schema(description = "상품 이미지 URL", example = "https://cdn.example.com/images/white-tshirt.jpg") String imageUrl,
        @Schema(description = "카테고리 ID", example = "2") Long categoryId,
        @Schema(description = "상품 상태", example = "ON_SALE") ProductStatus status,
        @Schema(description = "옵션 목록") List<OptionDto> options,
        @Schema(description = "Variant 목록") List<VariantDto> variants
) {
    public record OptionDto(
            @Schema(description = "옵션 이름", example = "색상") String name,
            @Schema(description = "옵션값 목록", example = "[\"BLACK\", \"WHITE\"]") List<String> values
    ) {}

    public record VariantDto(
            @Schema(description = "Variant ID", example = "10") Long id,
            @Schema(description = "가격 (원)", example = "29000") long price,
            @Schema(description = "재고 수량", example = "50") int stock,
            @Schema(description = "Variant 상태", example = "ON_SALE") VariantStatus status,
            @Schema(description = "선택된 옵션값 목록", example = "[\"BLACK\", \"M\"]") List<String> options
    ) {}
}