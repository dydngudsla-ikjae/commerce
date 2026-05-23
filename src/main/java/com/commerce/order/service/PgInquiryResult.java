package com.commerce.order.service;

/**
 * PG inquiry 결과. status가 SUCCESS일 때만 pgTransactionId가 존재한다.
 */
public record PgInquiryResult(PgPaymentStatus status, String pgTransactionId) {

    public boolean isSuccess() {
        return status == PgPaymentStatus.SUCCESS;
    }
}