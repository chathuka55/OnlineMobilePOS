package com.possaas.sales.repository;

import com.possaas.sales.domain.Refund;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    Optional<Refund> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT r FROM Refund r LEFT JOIN FETCH r.lines WHERE r.id = :id")
    Optional<Refund> findByIdWithLines(@Param("id") UUID id);
}
