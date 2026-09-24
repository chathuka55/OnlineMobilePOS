package com.possaas.sales.repository;

import com.possaas.sales.domain.Payment;
import com.possaas.sales.domain.PaymentDocumentType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByDocumentTypeAndDocumentIdOrderByReceivedAtAsc(
            PaymentDocumentType documentType, UUID documentId);

    /**
     * Cash taken at a till during a shift, split by what it was taken against.
     * Reversed payments are excluded: voiding a bill puts the money back, so it
     * was never in the drawer at close.
     */
    @Query("""
            select p.documentType, sum(p.amount)
              from Payment p
             where p.outletId = :outletId
               and p.method = com.possaas.sales.domain.PaymentMethod.CASH
               and p.direction = com.possaas.sales.domain.PaymentDirection.IN
               and p.reversedAt is null
               and p.receivedAt >= :from
               and p.receivedAt < :to
             group by p.documentType
            """)
    List<Object[]> sumCashInByDocumentType(@Param("outletId") UUID outletId,
                                           @Param("from") Instant from,
                                           @Param("to") Instant to);

    /** Cash handed back as refunds during the same window. */
    @Query("""
            select coalesce(sum(p.amount), 0)
              from Payment p
             where p.outletId = :outletId
               and p.method = com.possaas.sales.domain.PaymentMethod.CASH
               and p.direction = com.possaas.sales.domain.PaymentDirection.OUT
               and p.reversedAt is null
               and p.receivedAt >= :from
               and p.receivedAt < :to
            """)
    java.math.BigDecimal sumCashOut(@Param("outletId") UUID outletId,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to);
}
