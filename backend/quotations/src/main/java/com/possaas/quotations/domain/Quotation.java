package com.possaas.quotations.domain;

import com.possaas.common.jpa.TenantEntity;
import com.possaas.common.money.Money;
import com.possaas.sales.domain.DiscountType;
import com.possaas.sales.domain.PriceMode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "quotations")
@Getter
@Setter
@NoArgsConstructor
public class Quotation extends TenantEntity {

    @Column(name = "outlet_id", nullable = false)
    private UUID outletId;

    @Column(name = "quotation_number", nullable = false)
    private String quotationNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private QuotationStatus status = QuotationStatus.DRAFT;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "currency", nullable = false)
    private String currency = "LKR";

    @Enumerated(EnumType.STRING)
    @Column(name = "price_mode", nullable = false)
    private PriceMode priceMode = PriceMode.RETAIL;

    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = Money.ZERO;

    @Column(name = "line_discount_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineDiscountTotal = Money.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "quote_discount_type", nullable = false)
    private DiscountType quoteDiscountType = DiscountType.NONE;

    @Column(name = "quote_discount_input", nullable = false, precision = 14, scale = 2)
    private BigDecimal quoteDiscountInput = Money.ZERO;

    @Column(name = "quote_discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal quoteDiscountAmount = Money.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxTotal = Money.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal grandTotal = Money.ZERO;

    @Column(name = "quoted_at", nullable = false)
    private Instant quotedAt = Instant.now();

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "converted_at")
    private Instant convertedAt;

    @Column(name = "converted_bill_id")
    private UUID convertedBillId;

    @Column(name = "terms")
    private String terms;

    @Column(name = "note")
    private String note;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineNumber ASC")
    private List<QuotationLine> lines = new ArrayList<>();

    public void addLine(QuotationLine line) {
        line.setQuotation(this);
        lines.add(line);
    }

    public boolean isEditable() {
        return status == QuotationStatus.DRAFT || status == QuotationStatus.SENT;
    }
}
