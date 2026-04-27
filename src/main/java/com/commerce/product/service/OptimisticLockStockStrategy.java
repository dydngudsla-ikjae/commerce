package com.commerce.product.service;

import com.commerce.global.exception.BusinessException;
import com.commerce.global.exception.ErrorCode;
import com.commerce.product.domain.ProductVariant;
import com.commerce.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
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
    public void deduct(List<StockDeductionCommand> commands) {
        commands.stream()
                .sorted(Comparator.comparingLong(StockDeductionCommand::variantId))
                .forEach(cmd -> {
                    ProductVariant variant = productVariantRepository.findById(cmd.variantId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));
                    variant.decreaseStock(cmd.quantity());
                });
    }

    @Override
    public void restore(List<StockDeductionCommand> commands) {
        commands.stream()
                .sorted(Comparator.comparingLong(StockDeductionCommand::variantId))
                .forEach(cmd -> {
                    ProductVariant variant = productVariantRepository.findById(cmd.variantId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));
                    variant.increaseStock(cmd.quantity());
                });
    }

    @Override
    public boolean isRetryable(Exception e) {
        return e instanceof OptimisticLockingFailureException;
    }
}