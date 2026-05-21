package com.commerce.product.service;

import com.commerce.global.exception.BusinessException;
import com.commerce.global.exception.ErrorCode;
import com.commerce.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
// SQL WHERE 조건으로 재고 차감을 원자적으로 처리 — @Version 낙관적 락 불필요, isRetryable=false
public class AtomicUpdateStockStrategy implements StockDeductionStrategy {

    private final ProductVariantRepository productVariantRepository;

    @Override
    public void reserve(List<StockDeductionCommand> commands) {
        commands.stream()
                .sorted(Comparator.comparingLong(StockDeductionCommand::variantId))
                .forEach(cmd -> {
                    int updated = productVariantRepository.reserveStock(cmd.variantId(), cmd.quantity());
                    if (updated == 0) {
                        throw new BusinessException(ErrorCode.OUT_OF_STOCK);
                    }
                });
    }

    @Override
    public void release(List<StockDeductionCommand> commands) {
        commands.stream()
                .sorted(Comparator.comparingLong(StockDeductionCommand::variantId))
                .forEach(cmd -> productVariantRepository.releaseStock(cmd.variantId(), cmd.quantity()));
    }

    @Override
    public void confirm(List<StockDeductionCommand> commands) {
        commands.stream()
                .sorted(Comparator.comparingLong(StockDeductionCommand::variantId))
                .forEach(cmd -> productVariantRepository.confirmStock(cmd.variantId(), cmd.quantity()));
    }

    @Override
    public void refund(List<StockDeductionCommand> commands) {
        commands.stream()
                .sorted(Comparator.comparingLong(StockDeductionCommand::variantId))
                .forEach(cmd -> productVariantRepository.refundStock(cmd.variantId(), cmd.quantity()));
    }

    @Override
    public boolean isRetryable(Exception e) {
        return false;
    }
}