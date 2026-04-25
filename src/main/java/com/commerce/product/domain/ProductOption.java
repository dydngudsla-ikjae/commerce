package com.commerce.product.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product_options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private String name;

    @OneToMany(mappedBy = "option", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductOptionValue> values = new ArrayList<>();

    public static ProductOption create(Product product, String name) {
        ProductOption option = new ProductOption();
        option.product = product;
        option.name = name;
        return option;
    }

    public ProductOptionValue addValue(String value) {
        ProductOptionValue optionValue = ProductOptionValue.create(this, value);
        this.values.add(optionValue);
        return optionValue;
    }
}