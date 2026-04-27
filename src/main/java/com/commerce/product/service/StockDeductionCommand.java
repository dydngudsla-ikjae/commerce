package com.commerce.product.service;

public record StockDeductionCommand(Long variantId, int quantity) {
}
