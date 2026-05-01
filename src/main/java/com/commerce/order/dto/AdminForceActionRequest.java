package com.commerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AdminForceActionRequest(
        @Schema(description = "처리 사유 (필수)", example = "PG사 확인 후 수동 처리")
        @NotBlank(message = "reason은 필수입니다.")
        String reason
) {
}