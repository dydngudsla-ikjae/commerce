package com.commerce.product.dto;

import com.commerce.product.domain.VariantStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record VariantResponse(
        @Schema(description = "Variant ID", example = "10") Long id,
        @Schema(description = "가격 (원)", example = "29000") long price,
        @Schema(description = "재고 수량", example = "50") int stock,
        @Schema(description = "Variant 상태", example = "ON_SALE") VariantStatus status,
        @Schema(description = "선택된 옵션값 목록", example = "[\"BLACK\", \"M\"]") List<String> options
) {
}