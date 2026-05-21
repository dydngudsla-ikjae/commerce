package com.commerce.product.service;

import java.util.List;

/**
 * 재고 차감 전략 인터페이스.
 *
 * 재고 흐름:
 *   reserve()  → 주문 생성 시 재고 예약 (availableStock 감소)
 *   confirm()  → 결제 완료 시 실제 재고 차감 (stock 감소, reservedStock 해제)
 *   release()  → 취소/만료 시 예약 반환 (reservedStock 해제)
 *   refund()   → 결제 완료 후 취소 시 실제 재고 복구 (stock 증가)
 *
 * 구현체: OptimisticLockStockStrategy(@Primary), AtomicUpdateStockStrategy
 */
public interface StockDeductionStrategy {
    void reserve(List<StockDeductionCommand> commands);
    void release(List<StockDeductionCommand> commands);
    void confirm(List<StockDeductionCommand> commands);
    void refund(List<StockDeductionCommand> commands);
    // true 반환 시 OrderService가 지수 백오프 후 재시도
    boolean isRetryable(Exception e);
}