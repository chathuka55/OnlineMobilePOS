package com.possaas.sales.domain;

import com.possaas.common.jpa.TenantOwnedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "refunds")
@Getter
@Setter
@NoArgsConstructor
public class Refund extends TenantOwnedEntity {

    @Column(name = "outlet_id")
    private UUID outletId;

    @Column(name = "refund_number", nullable = false)
    private String refundNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private RefundSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "source_number")
    private String sourceNumber;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "customer_name")
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_scope", nullable = false)
    private RefundType refundScope = RefundType.PARTIAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement", nullable = false)
    private RefundSettlement settlement = RefundSettlement.CASH;

    @Column(name = "currency", nullable = false)
    private String currency = "LKR";

    @Column(name = "refund_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "restock", nullable = false)
    private boolean restock = true;

    @Column(name = "reason")
    private String reason;

    @Column(name = "note")
    private String note;

    @Column(name = "credit_note_id")
    private UUID creditNoteId;

    @Column(name = "refunded_at", nullable = false)
    private Instant refundedAt = Instant.now();

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by")
    private UUID createdBy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "refund", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RefundLine> lines = new ArrayList<>();

    public void addLine(RefundLine line) {
        line.setRefund(this);
        lines.add(line);
    }
}
