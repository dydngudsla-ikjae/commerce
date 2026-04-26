package com.commerce.order.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private long price;

    @Column(nullable = false)
    private int quantity;

    public static OrderItem create(Order order, Long variantId, String productName, long price, int quantity) {
        OrderItem item = new OrderItem();
        item.order = order;
        item.variantId = variantId;
        item.productName = productName;
        item.price = price;
        item.quantity = quantity;
        return item;
    }
}