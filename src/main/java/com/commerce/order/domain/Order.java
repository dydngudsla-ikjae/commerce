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

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private long totalAmount;

    @Column(name = "pg_transaction_id")
    private String pgTransactionId;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

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
        this.pgTransactionId = pgTransactionId;
    }

    public void confirmPaid() {
        if (this.status == OrderStatus.PAID) {
            return;
        }
        if (this.status != OrderStatus.PAYMENT_IN_PROGRESS && this.status != OrderStatus.PAYMENT_FAILED) {
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
}