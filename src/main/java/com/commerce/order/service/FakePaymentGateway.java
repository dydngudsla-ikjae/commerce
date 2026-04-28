package com.commerce.order.service;

import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FakePaymentGateway implements PaymentGateway {

    private final ConcurrentHashMap<Long, PaymentResult> processed = new ConcurrentHashMap<>();

    @Override
    public PaymentResult charge(Long orderId, long amount) {
        // 실제 PG사 API 호출 위치
        // orderId를 멱등 키로 사용 — 동일 orderId 재호출 시 같은 transactionId 반환
        return processed.computeIfAbsent(orderId, id -> new PaymentResult(UUID.randomUUID().toString()));
    }
}