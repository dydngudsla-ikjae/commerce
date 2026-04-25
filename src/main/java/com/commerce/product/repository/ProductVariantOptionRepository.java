package com.commerce.product.repository;

import com.commerce.product.domain.ProductVariantOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductVariantOptionRepository extends JpaRepository<ProductVariantOption, Long> {

    @Query("SELECT vo FROM ProductVariantOption vo JOIN FETCH vo.optionValue WHERE vo.variant.id = :variantId")
    List<ProductVariantOption> findByVariantId(@Param("variantId") Long variantId);

    @Query("""
        SELECT vo FROM ProductVariantOption vo
        JOIN FETCH vo.optionValue
        WHERE vo.variant.product.id = :productId
        """)
    List<ProductVariantOption> findByProductId(@Param("productId") Long productId);
}