package com.possaas.catalog.domain;

import com.possaas.common.jpa.TenantEntity;
import com.possaas.common.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "goods_received_notes")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class GoodsReceivedNote extends TenantEntity {

    @Column(name = "outlet_id", nullable = false)
    private UUID outletId;

    @Column(name = "grn_number", nullable = false)
    private String grnNumber;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "supplier_invoice_no")
    private String supplierInvoiceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GrnStatus status = GrnStatus.DRAFT;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = Money.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount = Money.ZERO;

    @Column(name = "total", nullable = false, precision = 14, scale = 2)
    private BigDecimal total = Money.ZERO;

    @Column(name = "notes")
    private String notes;

    @Column(name = "posted_at")
    private Instant postedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    public void markPosted() {
        this.status = GrnStatus.POSTED;
        this.postedAt = Instant.now();
    }
}
