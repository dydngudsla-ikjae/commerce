package com.commerce.order.domain;

import com.commerce.global.exception.BusinessException;
import com.commerce.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    // 낙관적 락: 동시 상태 변경 충돌 감지. OrderService 에서 재시도 루프와 함께 사용.
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private long totalAmount;

    // PG 청구 성공 시 저장. NULL이면 charge() 자체가 실패한 것이므로 스케줄러 재시도 대상에서 제외.
    @Column(name = "pg_transaction_id")
    private String pgTransactionId;

    // PaymentRetryScheduler가 MAX_RETRY_COUNT 도달 여부 판단에 사용
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    // 마지막 재시도 시각. NULL이면 updatedAt 기준으로 threshold를 비교 (OrderRepository 쿼리 참조)
    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static Order create(Long memberId, long totalAmount) {
        Order order = new Order();
        order.memberId = memberId;
        order.status = OrderStatus.PENDING;
        order.totalAmount = totalAmount;
        return order;
    }

    public void startPayment() {
        if (this.status != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }
        this.status = OrderStatus.PAYMENT_IN_PROGRESS;
    }

    public void savePgTransactionId(String pgTransactionId) {
        if (this.pgTransactionId != null) {
            if (!this.pgTransactionId.equals(pgTransactionId)) {
                throw new BusinessException(ErrorCode.PG_TRANSACTION_ID_MISMATCH);
            }
            return; // 같은 값 — 멱등
        }
        this.pgTransactionId = pgTransactionId;
    }

    public void confirmPaid() {
        // 이미 PAID 면 멱등 처리 — 스케줄러와 webhook이 동시에 호출해도 안전
        if (this.status == OrderStatus.PAID) {
            return;
        }
        if (this.status != OrderStatus.PAYMENT_IN_PROGRESS) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }
        this.status = OrderStatus.PAID;
    }

    // 관리자 전용: PAYMENT_FAILED → PAID. 일반 결제 confirm 경로(confirmPaid)와 분리.
    public void forceConfirmByAdmin() {
        if (this.status != OrderStatus.PAYMENT_FAILED) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }
        this.status = OrderStatus.PAID;
    }

    public void cancelByPgFailure() {
        if (this.status != OrderStatus.PAYMENT_IN_PROGRESS) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }
        this.status = OrderStatus.CANCELLED;
    }

    public void markPaymentFailed() {
        if (this.status != OrderStatus.PAYMENT_IN_PROGRESS) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }
        this.status = OrderStatus.PAYMENT_FAILED;
    }

    public void cancelByAdmin() {
        if (this.status != OrderStatus.PAYMENT_FAILED) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }
        this.status = OrderStatus.CANCELLED;
    }

    public void incrementRetry(LocalDateTime now) {
        this.retryCount++;
        this.lastRetryAt = now;
    }

    public void cancel() {
        if (this.status != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }
        this.status = OrderStatus.CANCELLED;
    }

    public void startCancellation() {
        if (this.status != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }
        this.status = OrderStatus.CANCEL_IN_PROGRESS;
    }

    public void completeCancel() {
        if (this.status == OrderStatus.CANCELLED) {
            return; // 멱등 — 스케줄러 중복 호출 안전
        }
        if (this.status != OrderStatus.CANCEL_IN_PROGRESS) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }
        this.status = OrderStatus.CANCELLED;
    }
}