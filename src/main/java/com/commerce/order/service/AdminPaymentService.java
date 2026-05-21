package com.commerce.order.service;

import com.commerce.global.exception.BusinessException;
import com.commerce.global.exception.ErrorCode;
import com.commerce.order.domain.AuditLog;
import com.commerce.order.domain.Order;
import com.commerce.order.domain.OrderStatus;
import com.commerce.order.domain.PaymentVerification;
import com.commerce.order.dto.OrderResponse;
import com.commerce.order.dto.VerifyPaymentResponse;
import com.commerce.order.repository.AuditLogRepository;
import com.commerce.order.repository.OrderRepository;
import com.commerce.order.repository.PaymentVerificationRepository;
import com.commerce.product.service.StockDeductionCommand;
import com.commerce.product.service.StockDeductionStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminPaymentService {

    private final OrderRepository orderRepository;
    private final PaymentVerificationRepository paymentVerificationRepository;
    private final AuditLogRepository auditLogRepository;
    private final PaymentGateway paymentGateway;
    private final StockDeductionStrategy stockDeductionStrategy;

    @Transactional
    public VerifyPaymentResponse verifyPayment(Long orderId, String verifiedBy) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        PgPaymentStatus pgStatus = paymentGateway.query(orderId);
        String pgTransactionId = order.getPgTransactionId();

        PaymentVerification verification = PaymentVerification.create(
                orderId, pgTransactionId, pgStatus, verifiedBy);
        paymentVerificationRepository.save(verification);

        return new VerifyPaymentResponse(orderId, pgTransactionId, pgStatus, verification.getVerifiedAt());
    }

    @Transactional
    public OrderResponse forceConfirm(Long orderId, String reason, String performedBy) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // AuditLog.idempotencyKey 유니크 제약이 이중 저장을 막지만, 저장 전에도 조기 반환해 불필요한 재고 연산 방지
        String idempotencyKey = "FORCE_CONFIRM:" + orderId;
        if (auditLogRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return OrderResponse.from(order);
        }

        if (order.getStatus() != OrderStatus.PAYMENT_FAILED) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }

        // 관리자가 verifyPayment()를 먼저 실행해 PG 상태를 기록해야만 강제 처리 가능
        PaymentVerification verification = paymentVerificationRepository.findFirstByOrderIdOrderByVerifiedAtDesc(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_VERIFY_REQUIRED));

        // PG에서 실제로 SUCCESS가 아니면 강제 확정 차단 — 이중 결제/손실 방지
        if (verification.getPgStatus() != PgPaymentStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.PAYMENT_VERIFY_MISMATCH);
        }

        order.confirmPaid();

        List<StockDeductionCommand> commands = order.getItems().stream()
                .map(item -> new StockDeductionCommand(item.getVariantId(), item.getQuantity()))
                .toList();
        stockDeductionStrategy.confirm(commands);

        auditLogRepository.save(AuditLog.create(orderId, "FORCE_CONFIRM", performedBy, reason, idempotencyKey));

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse forceCancel(Long orderId, String reason, String performedBy) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        String idempotencyKey = "FORCE_CANCEL:" + orderId;
        if (auditLogRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return OrderResponse.from(order);
        }

        if (order.getStatus() != OrderStatus.PAYMENT_FAILED) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }

        PaymentVerification verification = paymentVerificationRepository.findFirstByOrderIdOrderByVerifiedAtDesc(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_VERIFY_REQUIRED));

        // PG에서 실제로 FAIL이 아니면 강제 취소 차단 — 실제 결제된 금액을 취소 처리하는 실수 방지
        if (verification.getPgStatus() != PgPaymentStatus.FAIL) {
            throw new BusinessException(ErrorCode.PAYMENT_VERIFY_MISMATCH);
        }

        List<StockDeductionCommand> commands = order.getItems().stream()
                .map(item -> new StockDeductionCommand(item.getVariantId(), item.getQuantity()))
                .toList();
        stockDeductionStrategy.release(commands);
        order.cancelByAdmin();

        auditLogRepository.save(AuditLog.create(orderId, "FORCE_CANCEL", performedBy, reason, idempotencyKey));

        return OrderResponse.from(order);
    }
}