package com.commerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateVariantRequest(
        @Schema(description = "가격 (원, 0 이상)", example = "29000") @NotNull @Min(0) Long price,
        @Schema(description = "재고 수량 (0 이상, 0이면 SOLD_OUT으로 생성)", example = "100") @NotNull @Min(0) Integer stock,
        @Schema(description = "옵션값 ID 목록 (없으면 옵션 없는 Variant)", example = "[1, 3]") List<Long> optionValueIds
) {
}