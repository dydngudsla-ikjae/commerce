package com.commerce.product.domain;

import com.commerce.global.exception.BusinessException;
import com.commerce.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product_variants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private long price;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private int reservedStock = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VariantStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "variant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariantOption> variantOptions = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static ProductVariant create(Product product, long price, int stock) {
        ProductVariant variant = new ProductVariant();
        variant.product = product;
        variant.price = price;
        variant.stock = stock;
        variant.reservedStock = 0;
        variant.status = stock > 0 ? VariantStatus.ON_SALE : VariantStatus.SOLD_OUT;
        return variant;
    }

    public int getAvailableStock() {
        return this.stock - this.reservedStock;
    }

    public void reserve(int quantity) {
        if (getAvailableStock() < quantity) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
        this.reservedStock += quantity;
        if (getAvailableStock() == 0) {
            this.status = VariantStatus.SOLD_OUT;
        }
    }

    public void release(int quantity) {
        if (this.reservedStock < quantity) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
        this.reservedStock -= quantity;
        if (this.status == VariantStatus.SOLD_OUT && getAvailableStock() > 0) {
            this.status = VariantStatus.ON_SALE;
        }
    }

    public void confirm(int quantity) {
        if (this.reservedStock < quantity || this.stock < quantity) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
        this.stock -= quantity;
        this.reservedStock -= quantity;
    }

    public void updatePriceAndStatus(long price, VariantStatus status) {
        this.price = price;
        this.status = status;
    }

    public void updateStock(int stock) {
        this.stock = stock;
        this.status = getAvailableStock() > 0 ? VariantStatus.ON_SALE : VariantStatus.SOLD_OUT;
    }

    public void decreaseStock(int quantity) {
        if (getAvailableStock() < quantity) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
        this.stock -= quantity;
        this.status = getAvailableStock() > 0 ? VariantStatus.ON_SALE : VariantStatus.SOLD_OUT;
    }
}