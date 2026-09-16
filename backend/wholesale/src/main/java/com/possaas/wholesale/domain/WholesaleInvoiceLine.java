package com.possaas.wholesale.domain;

import com.possaas.common.jpa.TenantOwnedEntity;
import com.possaas.common.money.Money;
import com.possaas.sales.domain.DiscountType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "wholesale_invoice_lines")
@Getter
@Setter
@NoArgsConstructor
public class WholesaleInvoiceLine extends TenantOwnedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wholesale_invoice_id", nullable = false)
    private WholesaleInvoice invoice;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "line_number", nullable = false)
    private short lineNumber;

    @Column(name = "item_sku", nullable = false)
    private String itemSku;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitCost = Money.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_mode", nullable = false)
    private WholesalePriceMode priceMode = WholesalePriceMode.WHOLESALE;

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
    private BigDecimal discountInput = Money.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount = Money.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "tax_rate_id")
    private UUID taxRateId;

    @Column(name = "tax_rate_percent", nullable = false, precision = 9, scale = 4)
    private BigDecimal taxRatePercent = Money.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount = Money.ZERO;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "warranty_label")
    private String warrantyLabel;

    @Column(name = "quantity_returned", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantityReturned = Money.quantity(BigDecimal.ZERO);

    @OneToMany(mappedBy = "invoiceLine", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<WholesaleInvoiceLineSerial> serials = new ArrayList<>();

    public void addSerial(WholesaleInvoiceLineSerial serial) {
        serial.setInvoiceLine(this);
        serials.add(serial);
    }
}
