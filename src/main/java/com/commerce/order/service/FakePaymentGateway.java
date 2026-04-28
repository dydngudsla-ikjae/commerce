package com.commerce.order.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class FakePaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult charge(Long orderId, long amount) {
        // 실제 PG사 API 호출 위치
        return new PaymentResult(UUID.randomUUID().toString());
    }
}