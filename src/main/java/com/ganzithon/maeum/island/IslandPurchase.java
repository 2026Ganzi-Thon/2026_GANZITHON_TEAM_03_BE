package com.ganzithon.maeum.island;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "island_purchases")
class IslandPurchase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private Long woolTransactionId;

    @Column(nullable = false)
    private Instant createdAt;

    protected IslandPurchase() {}

    IslandPurchase(long userId, long itemId, int price, long woolTransactionId, Instant createdAt) {
        this.userId = userId;
        this.itemId = itemId;
        this.price = price;
        this.woolTransactionId = woolTransactionId;
        this.createdAt = createdAt;
    }
}
