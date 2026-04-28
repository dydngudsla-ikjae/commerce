package com.commerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateOrderRequest(
        @Schema(description = "주문 항목 목록 (1개 이상)")
        @NotEmpty @Valid List<OrderItemRequest> items
) {
}