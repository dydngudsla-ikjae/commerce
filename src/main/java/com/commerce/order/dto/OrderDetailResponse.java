package com.commerce.order.dto;

import com.commerce.order.domain.Order;
import com.commerce.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResponse(
        @Schema(description = "주문 ID", example = "1001") Long id,
        @Schema(description = "회원 ID", example = "42") Long memberId,
        @Schema(description = "주문 상태", example = "PAID") OrderStatus status,
        @Schema(description = "총 결제 금액 (원)", example = "59000") long totalAmount,
        @Schema(description = "주문 항목 목록") List<OrderItemResponse> items,
        @Schema(description = "주문 생성 시각") LocalDateTime createdAt
) {
    public static OrderDetailResponse from(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(OrderItemResponse::from)
                .toList();
        return new OrderDetailResponse(
                order.getId(),
                order.getMemberId(),
                order.getStatus(),
                order.getTotalAmount(),
                items,
                order.getCreatedAt()
        );
    }
}