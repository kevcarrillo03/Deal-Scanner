package com.kevincarrillo.dealscanner.db;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceCheckRepository extends JpaRepository<PriceCheck, Long> {

    @EntityGraph(attributePaths = "snapshots")
    Optional<PriceCheck> findFirstByProductUpcOrderByCheckedAtDesc(String upc);

    @EntityGraph(attributePaths = "snapshots")
    List<PriceCheck> findByProductUpcAndCheckedAtAfterOrderByCheckedAtDesc(String upc, Instant since);
}
