package com.commerce.order.service;

public interface PaymentGateway {
    // orderId는 멱등 키로 사용된다. 동일 orderId 재호출 시 이중 청구 없이 같은 결과를 반환해야 한다.
    PaymentResult charge(Long orderId, long amount);
}