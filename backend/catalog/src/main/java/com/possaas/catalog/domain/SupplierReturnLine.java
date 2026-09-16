package com.possaas.catalog.domain;

import com.possaas.common.jpa.TenantOwnedEntity;
import com.possaas.common.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "supplier_return_lines")
@Getter
@Setter
@NoArgsConstructor
public class SupplierReturnLine extends TenantOwnedEntity {

    @Column(name = "supplier_return_id", nullable = false, updatable = false)
    private UUID supplierReturnId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "item_serial_id")
    private UUID itemSerialId;

    @Column(name = "line_number", nullable = false)
    private short lineNumber;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitCost = Money.ZERO;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal = Money.ZERO;

    @Column(name = "reason")
    private String reason;

    /**
     * Whether the returned serial's warranty was still valid at the moment of
     * return. Null when the item isn't serial-tracked (no warranty window to
     * check) or has no warranty configured.
     */
    @Column(name = "within_warranty")
    private Boolean withinWarranty;
}
