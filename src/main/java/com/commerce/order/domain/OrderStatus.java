package com.commerce.order.domain;

public enum OrderStatus {
    PENDING,
    PAYMENT_IN_PROGRESS,
    PAID,           // final
    CANCELLED,      // final
    PAYMENT_FAILED  // 수동 개입 필요
}