package com.commerce.order.service;

import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class FakePaymentGateway implements PaymentGateway {

    private final ConcurrentHashMap<Long, PaymentResult> processed = new ConcurrentHashMap<>();
    private final AtomicBoolean chargeWillFail = new AtomicBoolean(false);
    private final ConcurrentHashMap<Long, PgPaymentStatus> queryOverrides = new ConcurrentHashMap<>();

    @Override
    public PaymentResult charge(Long orderId, long amount) {
        // 실제 PG사 API 호출 위치
        // orderId를 멱등 키로 사용 — 동일 orderId 재호출 시 같은 transactionId 반환
        if (chargeWillFail.get()) {
            throw new PgChargeException("FakePaymentGateway: charge will fail (test flag set)");
        }
        return processed.computeIfAbsent(orderId, id -> new PaymentResult(UUID.randomUUID().toString()));
    }

    @Override
    public PgPaymentStatus query(Long orderId) {
        if (queryOverrides.containsKey(orderId)) {
            return queryOverrides.get(orderId);
        }
        return processed.containsKey(orderId) ? PgPaymentStatus.SUCCESS : PgPaymentStatus.UNKNOWN;
    }

    // ==================== 테스트 제어 메서드 ====================

    public void setChargeWillFail(boolean fail) {
        chargeWillFail.set(fail);
    }

    public void setQueryResult(Long orderId, PgPaymentStatus status) {
        queryOverrides.put(orderId, status);
    }

    public void reset() {
        processed.clear();
        chargeWillFail.set(false);
        queryOverrides.clear();
    }
}