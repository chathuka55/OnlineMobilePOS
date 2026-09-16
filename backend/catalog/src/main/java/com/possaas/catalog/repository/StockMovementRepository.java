package com.possaas.catalog.repository;

import com.possaas.catalog.domain.StockMovement;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    List<StockMovement> findByItemIdOrderByOccurredAtDesc(UUID itemId);
}
