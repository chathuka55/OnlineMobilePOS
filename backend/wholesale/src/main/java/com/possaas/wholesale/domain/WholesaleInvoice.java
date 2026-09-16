package com.possaas.wholesale.domain;

import com.possaas.common.jpa.TenantEntity;
import com.possaas.common.money.Money;
import com.possaas.sales.domain.DiscountType;
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
@Table(name = "wholesale_invoices")
@Getter
@Setter
@NoArgsConstructor
public class WholesaleInvoice extends TenantEntity {

    @Column(name = "outlet_id", nullable = false)
    private UUID outletId;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WholesaleInvoiceStatus status = WholesaleInvoiceStatus.DRAFT;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "currency", nullable = false)
    private String currency = "LKR";

    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = Money.ZERO;

    @Column(name = "line_discount_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineDiscountTotal = Money.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_discount_type", nullable = false)
    private DiscountType invoiceDiscountType = DiscountType.NONE;

    @Column(name = "invoice_discount_input", nullable = false, precision = 14, scale = 2)
    private BigDecimal invoiceDiscountInput = Money.ZERO;

    @Column(name = "invoice_discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal invoiceDiscountAmount = Money.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxTotal = Money.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal grandTotal = Money.ZERO;

    @Column(name = "amount_paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountPaid = Money.ZERO;

    @Column(name = "outstanding_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal outstandingAmount = Money.ZERO;

    @Column(name = "cost_of_goods", nullable = false, precision = 14, scale = 2)
    private BigDecimal costOfGoods = Money.ZERO;

    @Column(name = "credit_limit_at_issue", precision = 14, scale = 2)
    private BigDecimal creditLimitAtIssue;

    @Column(name = "payment_terms_days", nullable = false)
    private short paymentTermsDays;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason")
    private String voidReason;

    @Column(name = "note")
    private String note;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineNumber ASC")
    private List<WholesaleInvoiceLine> lines = new ArrayList<>();

    public void addLine(WholesaleInvoiceLine line) {
        line.setInvoice(this);
        lines.add(line);
    }

    public boolean isDraft() {
        return status == WholesaleInvoiceStatus.DRAFT;
    }

    public boolean isOpen() {
        return status == WholesaleInvoiceStatus.POSTED
                || status == WholesaleInvoiceStatus.PARTIALLY_PAID
                || status == WholesaleInvoiceStatus.OVERDUE;
    }
}
