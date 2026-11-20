package com.ganzithon.maeum.island;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Set;

interface IslandPurchaseRepository extends JpaRepository<IslandPurchase, Long> {
    @Query("select p.itemId from IslandPurchase p where p.userId = :userId")
    Set<Long> findItemIdsByUserId(Long userId);

    boolean existsByUserIdAndItemId(Long userId, Long itemId);
}
