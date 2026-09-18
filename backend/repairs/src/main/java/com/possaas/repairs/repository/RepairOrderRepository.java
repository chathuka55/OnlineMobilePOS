package com.possaas.repairs.repository;

import com.possaas.repairs.domain.RepairOrder;
import com.possaas.repairs.domain.RepairOrderStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepairOrderRepository extends JpaRepository<RepairOrder, UUID> {

    Optional<RepairOrder> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            SELECT DISTINCT r FROM RepairOrder r
            LEFT JOIN FETCH r.lines
            WHERE r.id = :id
            """)
    Optional<RepairOrder> findByIdWithLines(@Param("id") UUID id);

    @Query("""
            SELECT r FROM RepairOrder r
             WHERE (:status IS NULL OR r.status = :status)
               AND (:customerId IS NULL OR r.customerId = :customerId)
               AND (:q IS NULL OR LOWER(r.repairNumber) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                    OR LOWER(r.customerName) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                    OR LOWER(COALESCE(r.deviceSerial, '')) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
            """)
    Page<RepairOrder> search(@Param("q") String q,
                             @Param("status") RepairOrderStatus status,
                             @Param("customerId") UUID customerId,
                             Pageable pageable);
}
