package com.ganzithon.maeum.island;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "island_items")
class IslandItem {
    @Id private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int placedOrder;

    @Column(name = "float_x", precision = 5, scale = 2)
    private BigDecimal floatX;

    @Column(name = "float_y", precision = 5, scale = 2)
    private BigDecimal floatY;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;

    protected IslandItem() {}

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getCategory() {
        return category;
    }

    int getPrice() {
        return price;
    }

    int getPlacedOrder() {
        return placedOrder;
    }

    BigDecimal getFloatX() {
        return floatX;
    }

    BigDecimal getFloatY() {
        return floatY;
    }
}
