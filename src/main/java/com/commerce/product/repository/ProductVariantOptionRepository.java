package com.commerce.product.repository;

import com.commerce.product.domain.ProductVariantOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface ProductVariantOptionRepository extends JpaRepository<ProductVariantOption, Long> {

    List<ProductVariantOption> findByVariantId(Long variantId);

    @Query("""
        SELECT vo FROM ProductVariantOption vo
        WHERE vo.variant.product.id = :productId
        """)
    List<ProductVariantOption> findByProductId(@Param("productId") Long productId);
}