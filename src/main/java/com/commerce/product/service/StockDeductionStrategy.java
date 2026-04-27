package com.commerce.product.service;

import java.util.List;

public interface StockDeductionStrategy {
    void deduct(List<StockDeductionCommand> commands);
    void restore(List<StockDeductionCommand> commands);
    boolean isRetryable(Exception e);
}