package com.commerce.order.dto;

import com.commerce.order.domain.Order;
import com.commerce.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record OrderResponse(
        @Schema(description = "주문 ID", example = "1001") Long id,
        @Schema(description = "회원 ID", example = "42") Long memberId,
        @Schema(description = "주문 상태", example = "PENDING") OrderStatus status,
        @Schema(description = "총 결제 금액 (원)", example = "59000") long totalAmount,
        @Schema(description = "주문 생성 시각") LocalDateTime createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getMemberId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }
}