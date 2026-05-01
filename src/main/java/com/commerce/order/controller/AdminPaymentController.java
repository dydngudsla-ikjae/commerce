package com.commerce.order.controller;

import com.commerce.global.exception.ErrorResponse;
import com.commerce.global.jwt.AuthMember;
import com.commerce.order.dto.AdminForceActionRequest;
import com.commerce.order.dto.OrderResponse;
import com.commerce.order.dto.VerifyPaymentResponse;
import com.commerce.order.service.AdminPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "관리자 결제", description = "결제 검증 및 강제 처리 API")
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final AdminPaymentService adminPaymentService;

    @Operation(
        summary = "결제 검증",
        description = "PG사에 결제 상태를 조회하고 PaymentVerification 레코드를 저장합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "검증 성공",
            content = @Content(schema = @Schema(implementation = VerifyPaymentResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "403", description = "권한 없음"),
        @ApiResponse(responseCode = "404", description = "주문 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/verify-payment")
    public ResponseEntity<VerifyPaymentResponse> verifyPayment(
            @Parameter(description = "주문 ID") @PathVariable Long id,
            @AuthenticationPrincipal AuthMember authMember) {
        String verifiedBy = authMember != null ? String.valueOf(authMember.id()) : "admin";
        VerifyPaymentResponse response = adminPaymentService.verifyPayment(id, verifiedBy);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "결제 강제 확정",
        description = "PAYMENT_FAILED 상태 주문을 관리자가 강제로 PAID로 전환합니다. " +
                      "최신 PaymentVerification.pgStatus == SUCCESS 인 경우에만 허용됩니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "강제 확정 성공",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "조건 불만족 (상태 오류, 검증 없음, pgStatus 불일치, reason 없음)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "403", description = "권한 없음"),
        @ApiResponse(responseCode = "404", description = "주문 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/force-confirm")
    public ResponseEntity<OrderResponse> forceConfirm(
            @Parameter(description = "주문 ID") @PathVariable Long id,
            @Valid @RequestBody AdminForceActionRequest request,
            @AuthenticationPrincipal AuthMember authMember) {
        String performedBy = authMember != null ? String.valueOf(authMember.id()) : "admin";
        OrderResponse response = adminPaymentService.forceConfirm(id, request.reason(), performedBy);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "결제 강제 취소",
        description = "PAYMENT_FAILED 상태 주문을 관리자가 강제로 CANCELLED로 전환합니다. " +
                      "최신 PaymentVerification.pgStatus == FAIL 인 경우에만 허용됩니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "강제 취소 성공",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "조건 불만족 (상태 오류, 검증 없음, pgStatus 불일치, reason 없음)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "403", description = "권한 없음"),
        @ApiResponse(responseCode = "404", description = "주문 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/force-cancel")
    public ResponseEntity<OrderResponse> forceCancel(
            @Parameter(description = "주문 ID") @PathVariable Long id,
            @Valid @RequestBody AdminForceActionRequest request,
            @AuthenticationPrincipal AuthMember authMember) {
        String performedBy = authMember != null ? String.valueOf(authMember.id()) : "admin";
        OrderResponse response = adminPaymentService.forceCancel(id, request.reason(), performedBy);
        return ResponseEntity.ok(response);
    }
}