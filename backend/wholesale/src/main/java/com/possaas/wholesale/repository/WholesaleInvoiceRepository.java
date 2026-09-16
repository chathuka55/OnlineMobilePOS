package com.possaas.wholesale.repository;

import com.possaas.wholesale.domain.WholesaleInvoice;
import com.possaas.wholesale.domain.WholesaleInvoiceStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WholesaleInvoiceRepository extends JpaRepository<WholesaleInvoice, UUID> {

    Optional<WholesaleInvoice> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            SELECT DISTINCT i FROM WholesaleInvoice i
            LEFT JOIN FETCH i.lines
            WHERE i.id = :id
            """)
    Optional<WholesaleInvoice> findByIdWithLines(@Param("id") UUID id);

    @Query("""
            SELECT i FROM WholesaleInvoice i
             WHERE (:status IS NULL OR i.status = :status)
               AND (:customerId IS NULL OR i.customerId = :customerId)
               AND (:q IS NULL OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(i.customerName) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<WholesaleInvoice> search(@Param("q") String q,
                                  @Param("status") WholesaleInvoiceStatus status,
                                  @Param("customerId") UUID customerId,
                                  Pageable pageable);

    @Query("""
            SELECT i FROM WholesaleInvoice i
             WHERE i.outstandingAmount > 0
               AND i.voidedAt IS NULL
               AND (:customerId IS NULL OR i.customerId = :customerId)
            """)
    Page<WholesaleInvoice> findOutstanding(@Param("customerId") UUID customerId, Pageable pageable);
}
