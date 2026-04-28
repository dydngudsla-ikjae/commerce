package com.commerce.order.controller;

import com.commerce.global.exception.ErrorResponse;
import com.commerce.global.jwt.AuthMember;
import com.commerce.order.dto.CreateOrderRequest;
import com.commerce.order.dto.OrderDetailResponse;
import com.commerce.order.dto.OrderResponse;
import com.commerce.order.service.OrderService;
import com.commerce.product.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "주문", description = "주문 생성, 결제, 취소, 조회 API")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(
        summary = "주문 생성",
        description = "Variant와 수량 목록을 받아 주문을 생성합니다. " +
                      "재고를 예약하며, 재고 부족 시 400을 반환합니다. " +
                      "주문은 PENDING 상태로 생성되고 결제 후 PAID로 전환됩니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "주문 생성 성공",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "재고 부족 또는 주문 불가 Variant",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "Variant 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @AuthenticationPrincipal AuthMember authMember,
            @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(authMember.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
        summary = "결제",
        description = "PENDING 상태 주문을 결제합니다. " +
                      "결제 성공 시 재고를 확정 차감하고 상태를 PAID로 변경합니다. " +
                      "본인 주문이 아니거나 PENDING 상태가 아니면 오류를 반환합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "결제 성공",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "결제 불가 상태",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "403", description = "본인 주문 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "주문 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/{id}/pay")
    public ResponseEntity<OrderResponse> pay(
            @AuthenticationPrincipal AuthMember authMember,
            @Parameter(description = "주문 ID", example = "1001") @PathVariable Long id) {
        OrderResponse response = orderService.pay(authMember.id(), id);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "주문 취소",
        description = "PENDING 상태 주문을 취소합니다. " +
                      "취소 시 예약된 재고가 즉시 해제됩니다. " +
                      "PAID 상태 이후 취소는 현재 지원하지 않습니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "취소 성공",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "취소 불가 상태",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "403", description = "본인 주문 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "주문 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancel(
            @AuthenticationPrincipal AuthMember authMember,
            @Parameter(description = "주문 ID", example = "1001") @PathVariable Long id) {
        OrderResponse response = orderService.cancel(authMember.id(), id);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "내 주문 목록 조회",
        description = "본인 주문을 최신순으로 페이지네이션하여 반환합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping
    public ResponseEntity<PageResponse<OrderResponse>> getMyOrders(
            @AuthenticationPrincipal AuthMember authMember,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") int size) {
        PageResponse<OrderResponse> response = orderService.getMyOrders(authMember.id(), page, size);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "주문 상세 조회",
        description = "주문 ID로 주문 상세 정보와 주문 항목 목록을 반환합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = OrderDetailResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "403", description = "본인 주문 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "주문 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{id}")
    public ResponseEntity<OrderDetailResponse> getOrderDetail(
            @AuthenticationPrincipal AuthMember authMember,
            @Parameter(description = "주문 ID", example = "1001") @PathVariable Long id) {
        OrderDetailResponse response = orderService.getOrderDetail(authMember.id(), id);
        return ResponseEntity.ok(response);
    }
}