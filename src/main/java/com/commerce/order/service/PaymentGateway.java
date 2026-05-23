package com.commerce.order.service;

public interface PaymentGateway {
    /**
     * PG에 결제를 요청한다.
     * orderId는 멱등 키 — 동일 orderId 재호출 시 이중 청구 없이 같은 결과를 반환해야 한다.
     * 실패 시 PgChargeException, 불명확 시(네트워크 타임아웃 등) 일반 Exception을 던진다.
     */
    PaymentResult charge(Long orderId, long amount);

    // orderId(멱등키)로 PG 결제 상태를 조회한다. SUCCESS이면 pgTransactionId도 함께 반환.
    PgInquiryResult query(Long orderId);

    // 결제 취소(환불)를 요청한다. 실패 시 예외를 던진다.
    void refund(Long orderId, String pgTransactionId);
}