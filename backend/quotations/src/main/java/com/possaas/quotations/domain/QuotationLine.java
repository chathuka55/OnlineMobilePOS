package com.possaas.quotations.domain;

import com.possaas.common.jpa.TenantOwnedEntity;
import com.possaas.common.money.Money;
import com.possaas.sales.domain.DiscountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "quotation_lines")
@Getter
@Setter
@NoArgsConstructor
public class QuotationLine extends TenantOwnedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    @Column(name = "item_id")
    private UUID itemId;

    @Column(name = "line_number", nullable = false)
    private short lineNumber;

    @Column(name = "item_sku")
    private String itemSku;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "description")
    private String description;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice = Money.ZERO;

    @Column(name = "gross_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossAmount = Money.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType = DiscountType.NONE;

    @Column(name = "discount_input", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountInput = Money.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount = Money.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal netAmount = Money.ZERO;

    @Column(name = "tax_rate_id")
    private UUID taxRateId;

    @Column(name = "tax_rate_percent", nullable = false, precision = 9, scale = 4)
    private BigDecimal taxRatePercent = Money.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount = Money.ZERO;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal = Money.ZERO;

    @Column(name = "warranty_label")
    private String warrantyLabel;
}
