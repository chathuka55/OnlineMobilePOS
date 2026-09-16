package com.possaas.sales.domain;

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
@Table(name = "credit_note_redemptions")
@Getter
@Setter
@NoArgsConstructor
public class CreditNoteRedemption extends TenantOwnedEntity {

    @Column(name = "credit_note_id", nullable = false)
    private UUID creditNoteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private CreditNoteDocumentType documentType;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "document_number")
    private String documentNumber;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt = Instant.now();

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "created_by")
    private UUID createdBy;
}
