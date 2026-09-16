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
@Table(name = "grn_lines")
@Getter
@Setter
@NoArgsConstructor
public class GrnLine extends TenantOwnedEntity {

    @Column(name = "grn_id", nullable = false, updatable = false)
    private UUID grnId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "line_number", nullable = false)
    private short lineNumber;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitCost = Money.ZERO;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal = Money.ZERO;

    @Column(name = "retail_price_at_receipt", precision = 14, scale = 2)
    private BigDecimal retailPriceAtReceipt;

    @Column(name = "warranty_months", nullable = false)
    private short warrantyMonths;
}
