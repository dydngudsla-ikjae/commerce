package com.commerce.product.repository;

import com.commerce.product.domain.ProductOptionValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductOptionValueRepository extends JpaRepository<ProductOptionValue, Long> {

    List<ProductOptionValue> findByOptionId(Long optionId);
}