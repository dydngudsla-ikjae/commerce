package com.commerce.product.service;

import java.util.List;

public interface StockDeductionStrategy {
    void reserve(List<StockDeductionCommand> commands);
    void release(List<StockDeductionCommand> commands);
    void confirm(List<StockDeductionCommand> commands);
    boolean isRetryable(Exception e);
}