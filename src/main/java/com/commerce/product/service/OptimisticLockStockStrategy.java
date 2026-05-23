package com.commerce.product.service;

import com.commerce.global.exception.BusinessException;
import com.commerce.global.exception.ErrorCode;
import com.commerce.product.domain.ProductVariant;
import com.commerce.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.StaleObjectStateException;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@Primary
@RequiredArgsConstructor
public class OptimisticLockStockStrategy implements StockDeductionStrategy {

    private final ProductVariantRepository productVariantRepository;

    @Override
    public void reserve(List<StockDeductionCommand> commands) {
        // variantId 오름차순 정렬: 여러 스레드가 동일 순서로 락을 잡아 데드락 방지
        commands.stream()
                .sorted(Comparator.comparingLong(StockDeductionCommand::variantId))
                .forEach(cmd -> {
                    ProductVariant variant = productVariantRepository.findById(cmd.variantId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));
                    variant.reserve(cmd.quantity());
                });
    }

    @Override
    public void release(List<StockDeductionCommand> commands) {
        commands.stream()
                .sorted(Comparator.comparingLong(StockDeductionCommand::variantId))
                .forEach(cmd -> {
                    ProductVariant variant = productVariantRepository.findById(cmd.variantId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));
                    variant.release(cmd.quantity());
                });
    }

    @Override
    public void confirm(List<StockDeductionCommand> commands) {
        commands.stream()
                .sorted(Comparator.comparingLong(StockDeductionCommand::variantId))
                .forEach(cmd -> {
                    ProductVariant variant = productVariantRepository.findById(cmd.variantId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));
                    variant.confirm(cmd.quantity());
                });
    }

    @Override
    public void refund(List<StockDeductionCommand> commands) {
        commands.stream()
                .sorted(Comparator.comparingLong(StockDeductionCommand::variantId))
                .forEach(cmd -> {
                    ProductVariant variant = productVariantRepository.findById(cmd.variantId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));
                    variant.refund(cmd.quantity());
                });
    }

    @Override
    public boolean isRetryable(Exception e) {
        Throwable current = e;
        int depth = 0;
        while (current != null && depth++ < 10) {
            if (current instanceof OptimisticLockingFailureException
                    || current instanceof StaleObjectStateException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}