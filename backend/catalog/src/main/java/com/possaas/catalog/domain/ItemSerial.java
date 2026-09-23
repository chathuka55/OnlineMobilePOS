package com.possaas.catalog.domain;

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
@Table(name = "item_serials")
@Getter
@Setter
@NoArgsConstructor
public class ItemSerial extends TenantEntity {

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "outlet_id")
    private UUID outletId;

    @Column(name = "serial_number", nullable = false)
    private String serialNumber;

    @Column(name = "imei1", length = 20)
    private String imei1;

    /** Second IMEI on a dual-SIM handset. */
    @Column(name = "imei2", length = 20)
    private String imei2;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_condition", nullable = false)
    private UnitCondition unitCondition = UnitCondition.NEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade")
    private UnitGrade grade;

    /** Percentage 0-100; below 80 is the conventional trade-in markdown trigger. */
    @Column(name = "battery_health")
    private Short batteryHealth;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SerialStatus status = SerialStatus.IN_STOCK;

    @Column(name = "grn_id")
    private UUID grnId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "cost_price", precision = 14, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "sold_at")
    private Instant soldAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sold_document_type")
    private SoldDocumentType soldDocumentType;

    @Column(name = "sold_document_id")
    private UUID soldDocumentId;

    @Column(name = "warranty_starts_on")
    private LocalDate warrantyStartsOn;

    @Column(name = "warranty_ends_on")
    private LocalDate warrantyEndsOn;

    @Column(name = "notes")
    private String notes;

    public boolean isAvailable() {
        return status == SerialStatus.IN_STOCK;
    }

    public void markSold(SoldDocumentType documentType, UUID documentId) {
        this.status = SerialStatus.SOLD;
        this.soldAt = Instant.now();
        this.soldDocumentType = documentType;
        this.soldDocumentId = documentId;
    }

    /** Customer sale return: unit is sellable again. */
    public void returnToStock() {
        this.status = SerialStatus.IN_STOCK;
        this.soldAt = null;
        this.soldDocumentType = null;
        this.soldDocumentId = null;
    }

    /** On the bench - not sellable, but still ours and still in stock valuation. */
    public void markInRepair() {
        this.status = SerialStatus.IN_REPAIR;
    }

    public void markDefective() {
        this.status = SerialStatus.DEFECTIVE;
        this.soldAt = null;
        this.soldDocumentType = null;
        this.soldDocumentId = null;
    }
}
