package com.possaas.sales.domain;

import com.possaas.common.jpa.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "credit_notes")
@Getter
@Setter
@NoArgsConstructor
public class CreditNote extends TenantEntity {

    @Column(name = "outlet_id")
    private UUID outletId;

    @Column(name = "credit_note_number", nullable = false)
    private String creditNoteNumber;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private CreditNoteSourceType sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "source_number")
    private String sourceNumber;

    @Column(name = "currency", nullable = false)
    private String currency = "LKR";

    @Column(name = "issued_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal issuedAmount;

    @Column(name = "balance_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal balanceAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CreditNoteStatus status = CreditNoteStatus.ACTIVE;

    @Column(name = "reason")
    private String reason;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    public void refreshStatus() {
        if (status == CreditNoteStatus.VOIDED || status == CreditNoteStatus.EXPIRED) {
            return;
        }
        if (balanceAmount.signum() == 0) {
            status = CreditNoteStatus.FULLY_USED;
        } else if (balanceAmount.compareTo(issuedAmount) < 0) {
            status = CreditNoteStatus.PARTIALLY_USED;
        } else {
            status = CreditNoteStatus.ACTIVE;
        }
    }
}
