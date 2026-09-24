package com.possaas.sales.repository;

import com.possaas.sales.domain.CashMovement;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashMovementRepository extends JpaRepository<CashMovement, UUID> {

    List<CashMovement> findByShiftIdOrderByOccurredAtAsc(UUID shiftId);
}
