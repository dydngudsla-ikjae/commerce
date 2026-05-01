package com.commerce.order.service;

/**
 * PG사 charge() 호출 실패 시 던지는 예외.
 * OrderService.pay()에서 catch → 재고 해제 + CANCELLED + BusinessException(PAYMENT_GATEWAY_ERROR) 전환.
 */
public class PgChargeException extends RuntimeException {

    public PgChargeException(String message) {
        super(message);
    }

    public PgChargeException(String message, Throwable cause) {
        super(message, cause);
    }
}