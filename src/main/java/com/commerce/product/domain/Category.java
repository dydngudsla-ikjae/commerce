package com.commerce.product.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    public static Category createRoot(String name) {
        Category category = new Category();
        category.name = name;
        category.parent = null;
        return category;
    }

    public static Category createChild(String name, Category parent) {
        Category category = new Category();
        category.name = name;
        category.parent = parent;
        return category;
    }

    public Long getParentId() {
        return parent != null ? parent.getId() : null;
    }
}