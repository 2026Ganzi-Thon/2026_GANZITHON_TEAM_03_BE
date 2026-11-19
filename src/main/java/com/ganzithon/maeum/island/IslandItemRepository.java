package com.ganzithon.maeum.island;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface IslandItemRepository extends JpaRepository<IslandItem, Long> {
    List<IslandItem> findByActiveTrueOrderByDisplayOrderAscIdAsc();

    Optional<IslandItem> findByIdAndActiveTrue(Long id);
}
