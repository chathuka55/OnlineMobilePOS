package com.possaas.wholesale.domain;

import com.possaas.common.jpa.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "customer_credit_ledger")
@Getter
@Setter
@NoArgsConstructor
public class CustomerCreditLedger extends TenantOwnedEntity {

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private CreditLedgerEntryType entryType;

    @Column(name = "amount_delta", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountDelta;

    @Column(name = "balance_after", nullable = false, precision = 14, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "document_type")
    private String documentType;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "document_number")
    private String documentNumber;

    @Column(name = "note")
    private String note;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "created_by")
    private UUID createdBy;
}
