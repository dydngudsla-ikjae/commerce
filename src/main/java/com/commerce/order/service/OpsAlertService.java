package com.commerce.order.service;

public interface OpsAlertService {
    void alertPaymentFailed(Long orderId);
    // PG inquiry에서도 결제 여부를 확인할 수 없는 경우 — 수동 개입 필요
    void alertPaymentUnknown(Long orderId);
}