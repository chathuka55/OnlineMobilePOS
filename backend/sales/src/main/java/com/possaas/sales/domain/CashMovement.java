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
@Table(name = "cash_movements")
@Getter
@Setter
@NoArgsConstructor
public class CashMovement extends TenantOwnedEntity {

    @Column(name = "shift_id", nullable = false)
    private UUID shiftId;

    @Column(name = "outlet_id")
    private UUID outletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false)
    private CashMovementType movementType;

    /** Always positive; direction comes from the type. */
    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason")
    private String reason;

    @Column(name = "reference")
    private String reference;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "created_by")
    private UUID createdBy;

    /** Signed contribution to the drawer: pay-ins add, payouts and drops remove. */
    public BigDecimal signedAmount() {
        return movementType == CashMovementType.PAY_IN ? amount : amount.negate();
    }
}
