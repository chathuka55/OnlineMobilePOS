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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "supplier_returns")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class SupplierReturn extends TenantEntity {

    @Column(name = "outlet_id", nullable = false)
    private UUID outletId;

    @Column(name = "return_number", nullable = false)
    private String returnNumber;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SupplierReturnStatus status = SupplierReturnStatus.DRAFT;

    @Column(name = "returned_at", nullable = false)
    private Instant returnedAt = Instant.now();

    @Column(name = "reason")
    private String reason;

    @Column(name = "total_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalValue = Money.ZERO;

    @Column(name = "notes")
    private String notes;

    @Column(name = "posted_at")
    private Instant postedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    public void markPosted() {
        this.status = SupplierReturnStatus.POSTED;
        this.postedAt = Instant.now();
    }
}
