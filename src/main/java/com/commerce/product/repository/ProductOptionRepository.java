package com.commerce.product.repository;

import com.commerce.product.domain.ProductOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

    List<ProductOption> findByProductId(Long productId);

    @Query("SELECT o FROM ProductOption o LEFT JOIN FETCH o.values WHERE o.product.id = :productId")
    List<ProductOption> findByProductIdWithValues(@Param("productId") Long productId);

    boolean existsByProductIdAndName(Long productId, String name);
}