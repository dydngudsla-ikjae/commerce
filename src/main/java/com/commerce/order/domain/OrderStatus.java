package com.commerce.order.domain;

/**
 * 주문 상태 머신
 *
 * PENDING ──────────────────────────────→ CANCELLED (사용자 취소 / 만료 스케줄러)
 *   │
 *   └─ startPayment() → PAYMENT_IN_PROGRESS
 *         │
 *         ├─ PG 명시 실패 → CANCELLED (재고 즉시 해제)
 *         ├─ confirmPayment() 성공 → PAID
 *         └─ retry 소진 → PAYMENT_FAILED  (관리자 수동 처리)
 *               │
 *               ├─ forceConfirm() → PAID
 *               └─ forceCancel() → CANCELLED
 *
 * PAID
 *   └─ startCancellation() → CANCEL_IN_PROGRESS  (환불 선점)
 *         └─ completeCancel() 성공 → CANCELLED  [final]
 *            (실패 시 CANCEL_IN_PROGRESS 유지 → CancelRetryScheduler 재시도)
 */
public enum OrderStatus {
    PENDING,
    PAYMENT_IN_PROGRESS,
    PAID,
    CANCEL_IN_PROGRESS, // PG 환불 선점 완료, DB 확정 대기
    CANCELLED,          // final
    PAYMENT_FAILED      // retry 소진 후 관리자 수동 개입 필요
}