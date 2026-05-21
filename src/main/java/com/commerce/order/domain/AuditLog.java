package com.commerce.order.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private String action;

    @Column(name = "performed_by")
    private String performedBy;

    @Column
    private String reason;

    // DB 유니크 제약으로 같은 키가 두 번 저장되면 예외 발생 → forceConfirm/forceCancel 이중 실행 방지
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static AuditLog create(Long orderId, String action, String performedBy,
                                  String reason, String idempotencyKey) {
        AuditLog log = new AuditLog();
        log.orderId = orderId;
        log.action = action;
        log.performedBy = performedBy;
        log.reason = reason;
        log.idempotencyKey = idempotencyKey;
        return log;
    }
}