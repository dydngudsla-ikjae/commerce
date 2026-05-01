package com.commerce.order.domain;

import com.commerce.order.service.PgPaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class PaymentVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "pg_transaction_id")
    private String pgTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "pg_status", nullable = false)
    private PgPaymentStatus pgStatus;

    @Column(name = "verified_by")
    private String verifiedBy;

    @CreatedDate
    @Column(name = "verified_at", nullable = false, updatable = false)
    private LocalDateTime verifiedAt;

    public static PaymentVerification create(Long orderId, String pgTransactionId,
                                             PgPaymentStatus pgStatus, String verifiedBy) {
        PaymentVerification v = new PaymentVerification();
        v.orderId = orderId;
        v.pgTransactionId = pgTransactionId;
        v.pgStatus = pgStatus;
        v.verifiedBy = verifiedBy;
        return v;
    }
}