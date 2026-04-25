package com.commerce.product.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_variant_options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductVariantOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_value_id", nullable = false)
    private ProductOptionValue optionValue;

    public static ProductVariantOption create(ProductVariant variant, ProductOptionValue optionValue) {
        ProductVariantOption vo = new ProductVariantOption();
        vo.variant = variant;
        vo.optionValue = optionValue;
        return vo;
    }
}