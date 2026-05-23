package com.commerce.order.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Profile("test")
@Service
public class FakePaymentGateway implements PaymentGateway {

    private final ConcurrentHashMap<Long, PaymentResult> processed = new ConcurrentHashMap<>();
    private final AtomicBoolean chargeWillFail = new AtomicBoolean(false);
    private final AtomicBoolean refundWillFail = new AtomicBoolean(false);
    private final ConcurrentHashMap<Long, PgInquiryResult> queryOverrides = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger refundCallCount = new java.util.concurrent.atomic.AtomicInteger(0);

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
    public PgInquiryResult query(Long orderId) {
        if (queryOverrides.containsKey(orderId)) {
            return queryOverrides.get(orderId);
        }
        PaymentResult result = processed.get(orderId);
        if (result != null) {
            return new PgInquiryResult(PgPaymentStatus.SUCCESS, result.transactionId());
        }
        return new PgInquiryResult(PgPaymentStatus.UNKNOWN, null);
    }

    @Override
    public void refund(Long orderId, String pgTransactionId) {
        refundCallCount.incrementAndGet();
        if (refundWillFail.get()) {
            throw new RuntimeException("FakePaymentGateway: refund will fail (test flag set)");
        }
        processed.remove(orderId);
    }

    // ==================== 테스트 제어 메서드 ====================

    public void setChargeWillFail(boolean fail) {
        chargeWillFail.set(fail);
    }

    public void setRefundWillFail(boolean fail) {
        refundWillFail.set(fail);
    }

    public void setQueryResult(Long orderId, PgPaymentStatus status) {
        queryOverrides.put(orderId, new PgInquiryResult(status, null));
    }

    public int getRefundCallCount() {
        return refundCallCount.get();
    }

    public void reset() {
        processed.clear();
        chargeWillFail.set(false);
        refundWillFail.set(false);
        queryOverrides.clear();
        refundCallCount.set(0);
    }
}