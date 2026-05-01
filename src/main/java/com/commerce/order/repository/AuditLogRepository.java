package com.commerce.order.repository;

import com.commerce.order.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Optional<AuditLog> findByIdempotencyKey(String idempotencyKey);
}