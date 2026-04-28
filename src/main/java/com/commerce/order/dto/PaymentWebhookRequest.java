package com.commerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PaymentWebhookRequest(
        @Schema(description = "결제 완료된 주문 ID (멱등 키)", example = "1001") Long orderId
) {
}