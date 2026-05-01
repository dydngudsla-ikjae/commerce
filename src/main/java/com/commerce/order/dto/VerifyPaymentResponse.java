package com.commerce.order.dto;

import com.commerce.order.service.PgPaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record VerifyPaymentResponse(
        @Schema(description = "주문 ID") Long orderId,
        @Schema(description = "PG 트랜잭션 ID") String pgTransactionId,
        @Schema(description = "PG 결제 상태") PgPaymentStatus pgStatus,
        @Schema(description = "검증 시각") LocalDateTime verifiedAt
) {
}