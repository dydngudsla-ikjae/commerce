package com.commerce.order.service;

public interface PaymentGateway {
    PaymentResult charge(Long orderId, long amount);
}