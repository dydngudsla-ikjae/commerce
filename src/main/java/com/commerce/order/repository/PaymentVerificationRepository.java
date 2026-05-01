package com.commerce.order.repository;

import com.commerce.order.domain.PaymentVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentVerificationRepository extends JpaRepository<PaymentVerification, Long> {

    Optional<PaymentVerification> findFirstByOrderIdOrderByVerifiedAtDesc(Long orderId);
}