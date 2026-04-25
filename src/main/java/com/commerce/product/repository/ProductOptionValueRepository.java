package com.commerce.product.repository;

import com.commerce.product.domain.ProductOptionValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductOptionValueRepository extends JpaRepository<ProductOptionValue, Long> {

    List<ProductOptionValue> findByOptionId(Long optionId);

    @Query("SELECT ov FROM ProductOptionValue ov WHERE ov.option.product.id = :productId AND ov.id IN :ids")
    List<ProductOptionValue> findByProductIdAndIdIn(@Param("productId") Long productId, @Param("ids") List<Long> ids);
}