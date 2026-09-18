package com.possaas.quotations.repository;

import com.possaas.quotations.domain.Quotation;
import com.possaas.quotations.domain.QuotationStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuotationRepository extends JpaRepository<Quotation, UUID> {

    @Query("""
            SELECT DISTINCT q FROM Quotation q
            LEFT JOIN FETCH q.lines
            WHERE q.id = :id
            """)
    Optional<Quotation> findByIdWithLines(@Param("id") UUID id);

    @Query("""
            SELECT q FROM Quotation q
             WHERE (:status IS NULL OR q.status = :status)
               AND (:customerId IS NULL OR q.customerId = :customerId)
               AND (:q IS NULL OR LOWER(q.quotationNumber) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                    OR LOWER(q.customerName) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
            """)
    Page<Quotation> search(@Param("q") String q,
                           @Param("status") QuotationStatus status,
                           @Param("customerId") UUID customerId,
                           Pageable pageable);
}
