package com.commerce.product.repository;

import com.commerce.product.domain.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByProductId(Long productId);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE ProductVariant v
        SET v.stock = v.stock - :quantity,
            v.status = CASE WHEN (v.stock - :quantity - v.reservedStock) > 0 THEN com.commerce.product.domain.VariantStatus.ON_SALE
                            ELSE com.commerce.product.domain.VariantStatus.SOLD_OUT END
        WHERE v.id = :id AND (v.stock - v.reservedStock) >= :quantity
        """)
    int decreaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE ProductVariant v
        SET v.reservedStock = v.reservedStock + :quantity,
            v.status = CASE WHEN v.status = com.commerce.product.domain.VariantStatus.STOPPED THEN v.status
                            WHEN (v.stock - v.reservedStock - :quantity) > 0 THEN com.commerce.product.domain.VariantStatus.ON_SALE
                            ELSE com.commerce.product.domain.VariantStatus.SOLD_OUT END
        WHERE v.id = :id AND (v.stock - v.reservedStock) >= :quantity
        """)
    int reserveStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE ProductVariant v
        SET v.reservedStock = v.reservedStock - :quantity,
            v.status = CASE WHEN (v.stock - v.reservedStock + :quantity) > 0
                                 AND v.status = com.commerce.product.domain.VariantStatus.SOLD_OUT
                            THEN com.commerce.product.domain.VariantStatus.ON_SALE
                            ELSE v.status END
        WHERE v.id = :id
        """)
    void releaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE ProductVariant v
        SET v.stock = v.stock - :quantity,
            v.reservedStock = v.reservedStock - :quantity
        WHERE v.id = :id
        """)
    void confirmStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Query("SELECT v FROM ProductVariant v JOIN FETCH v.product WHERE v.id IN :ids")
    List<ProductVariant> findByIdInWithProduct(@Param("ids") List<Long> ids);
}