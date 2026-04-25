package com.commerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record PageResponse<T>(
        @Schema(description = "조회된 항목 목록") List<T> content,
        @Schema(description = "현재 페이지 번호 (0부터 시작)", example = "0") int page,
        @Schema(description = "페이지 크기", example = "20") int size,
        @Schema(description = "전체 항목 수", example = "100") long totalElements,
        @Schema(description = "전체 페이지 수", example = "5") int totalPages
) {
}