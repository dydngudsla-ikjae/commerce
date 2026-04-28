package com.commerce.order.controller;

import com.commerce.global.exception.ErrorResponse;
import com.commerce.order.dto.OrderResponse;
import com.commerce.order.dto.PaymentWebhookRequest;
import com.commerce.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "결제 웹훅", description = "PG사 결제 완료 콜백 API")
@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final OrderService orderService;

    @Operation(
        summary = "결제 완료 웹훅",
        description = "PG사가 결제 완료 후 호출하는 콜백 엔드포인트입니다. " +
                      "orderId를 멱등 키로 처리하며, 이미 PAID 상태인 주문은 그대로 반환합니다. " +
                      "서버 다운 등으로 사용자 직접 결제가 실패했을 때 재시도 경로로 동작합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "결제 확정 성공 (기존 PAID 포함)",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "결제 불가 상태 (CANCELLED 등)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "주문 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/payment")
    public ResponseEntity<OrderResponse> paymentWebhook(@RequestBody PaymentWebhookRequest request) {
        OrderResponse response = orderService.confirmPayment(request.orderId());
        return ResponseEntity.ok(response);
    }
}