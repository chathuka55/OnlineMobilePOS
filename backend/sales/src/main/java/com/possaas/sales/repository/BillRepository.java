package com.possaas.sales.repository;

import com.possaas.sales.domain.Bill;
import com.possaas.sales.domain.BillStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillRepository extends JpaRepository<Bill, UUID> {

    Optional<Bill> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT b FROM Bill b LEFT JOIN FETCH b.lines WHERE b.id = :id")
    Optional<Bill> findByIdWithLines(@Param("id") UUID id);

    @Query("""
            SELECT b FROM Bill b
             WHERE (:status IS NULL OR b.status = :status)
               AND (:customerId IS NULL OR b.customerId = :customerId)
               AND (:from IS NULL OR b.billedAt >= :from)
               AND (:to IS NULL OR b.billedAt < :to)
               AND (:q IS NULL OR LOWER(b.billNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(b.customerName) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Bill> search(@Param("q") String q,
                      @Param("status") BillStatus status,
                      @Param("customerId") UUID customerId,
                      @Param("from") Instant from,
                      @Param("to") Instant to,
                      Pageable pageable);
}
