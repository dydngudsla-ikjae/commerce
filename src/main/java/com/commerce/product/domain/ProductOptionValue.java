package com.commerce.product.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_option_values")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOptionValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id", nullable = false)
    private ProductOption option;

    @Column(name = "option_value", nullable = false)
    private String value;

    public static ProductOptionValue create(ProductOption option, String value) {
        ProductOptionValue optionValue = new ProductOptionValue();
        optionValue.option = option;
        optionValue.value = value;
        return optionValue;
    }
}