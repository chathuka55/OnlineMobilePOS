package com.possaas.catalog.domain;

import com.possaas.common.jpa.TenantOwnedEntity;
import com.possaas.common.tenant.TenantContext;
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
@Table(name = "stock_movements")
@Getter
@Setter
@NoArgsConstructor
public class StockMovement extends TenantOwnedEntity {

    @Column(name = "outlet_id")
    private UUID outletId;

    @Column(name = "item_id", nullable = false, updatable = false)
    private UUID itemId;

    @Column(name = "item_serial_id")
    private UUID itemSerialId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, updatable = false)
    private MovementType movementType;

    @Column(name = "quantity_delta", nullable = false, precision = 14, scale = 3, updatable = false)
    private BigDecimal quantityDelta;

    @Column(name = "balance_after", nullable = false, precision = 14, scale = 3, updatable = false)
    private BigDecimal balanceAfter;

    @Column(name = "unit_cost", precision = 14, scale = 2, updatable = false)
    private BigDecimal unitCost;

    @Column(name = "reference_type", updatable = false)
    private String referenceType;

    @Column(name = "reference_id", updatable = false)
    private UUID referenceId;

    @Column(name = "reference_number", updatable = false)
    private String referenceNumber;

    @Column(name = "reason", updatable = false)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    public static StockMovement of(UUID itemId,
                                   MovementType type,
                                   BigDecimal quantityDelta,
                                   BigDecimal balanceAfter,
                                   BigDecimal unitCost,
                                   String referenceType,
                                   UUID referenceId,
                                   String referenceNumber,
                                   String reason,
                                   UUID itemSerialId) {
        StockMovement movement = new StockMovement();
        movement.itemId = itemId;
        movement.movementType = type;
        movement.quantityDelta = quantityDelta;
        movement.balanceAfter = balanceAfter;
        movement.unitCost = unitCost;
        movement.referenceType = referenceType;
        movement.referenceId = referenceId;
        movement.referenceNumber = referenceNumber;
        movement.reason = reason;
        movement.itemSerialId = itemSerialId;
        movement.outletId = TenantContext.current().map(TenantContext.Scope::outletId).orElse(null);
        movement.createdBy = TenantContext.userIdOrNull();
        return movement;
    }
}
