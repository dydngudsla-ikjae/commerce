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
public class AtomicUpdateStockStrategy implements StockDeductionStrategy {

    private final ProductVariantRepository productVariantRepository;

    @Override
    public void deduct(List<StockDeductionCommand> commands) {
        commands.stream()
                .sorted(Comparator.comparingLong(StockDeductionCommand::variantId))
                .forEach(cmd -> {
                    int updated = productVariantRepository.decreaseStock(cmd.variantId(), cmd.quantity());
                    if (updated == 0) {
                        throw new BusinessException(ErrorCode.OUT_OF_STOCK);
                    }
                });
    }
}