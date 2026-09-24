package com.possaas.sales.domain;

import com.possaas.common.jpa.TenantOwnedEntity;
import com.possaas.common.money.Money;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bill_lines")
@Getter
@Setter
@NoArgsConstructor
public class BillLine extends TenantOwnedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "line_number", nullable = false)
    private short lineNumber;

    @Column(name = "item_sku", nullable = false)
    private String itemSku;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "gross_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType = DiscountType.NONE;

    @Column(name = "discount_input", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountInput = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** This line's share of the bill-level discount. */
    @Column(name = "allocated_bill_discount", nullable = false, precision = 14, scale = 2)
    private BigDecimal allocatedBillDiscount = Money.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "tax_rate_id")
    private UUID taxRateId;

    @Column(name = "tax_rate_percent", nullable = false, precision = 9, scale = 4)
    private BigDecimal taxRatePercent = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "tax_inclusive", nullable = false)
    private boolean taxInclusive;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "warranty_label")
    private String warrantyLabel;

    @Column(name = "warranty_months", nullable = false)
    private short warrantyMonths;

    @Column(name = "warranty_ends_on")
    private LocalDate warrantyEndsOn;

    @Column(name = "quantity_returned", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantityReturned = BigDecimal.ZERO;

    @OneToMany(mappedBy = "billLine", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<BillLineSerial> serials = new ArrayList<>();

    public void addSerial(BillLineSerial serial) {
        serial.setBillLine(this);
        serials.add(serial);
    }

    public BigDecimal remainingQuantity() {
        return quantity.subtract(quantityReturned == null ? BigDecimal.ZERO : quantityReturned);
    }
}
