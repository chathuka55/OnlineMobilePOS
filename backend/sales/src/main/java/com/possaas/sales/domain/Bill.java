package com.possaas.sales.domain;

import com.possaas.common.jpa.TenantEntity;
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
@Table(name = "bills")
@Getter
@Setter
@NoArgsConstructor
public class Bill extends TenantEntity {

    @Column(name = "outlet_id", nullable = false)
    private UUID outletId;

    @Column(name = "bill_number", nullable = false)
    private String billNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BillStatus status = BillStatus.COMPLETED;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private BillChannel channel = BillChannel.RETAIL;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_mode", nullable = false)
    private PriceMode priceMode = PriceMode.RETAIL;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "customer_name", nullable = false)
    private String customerName = "Walk-in Customer";

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "currency", nullable = false)
    private String currency = "LKR";

    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "line_discount_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineDiscountTotal = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_discount_type", nullable = false)
    private DiscountType billDiscountType = DiscountType.NONE;

    @Column(name = "bill_discount_input", nullable = false, precision = 14, scale = 2)
    private BigDecimal billDiscountInput = BigDecimal.ZERO;

    @Column(name = "bill_discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal billDiscountAmount = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "rounding_adjustment", nullable = false, precision = 14, scale = 2)
    private BigDecimal roundingAdjustment = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Column(name = "credit_applied", nullable = false, precision = 14, scale = 2)
    private BigDecimal creditApplied = BigDecimal.ZERO;

    @Column(name = "amount_paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    /** Positive means the customer still owes; negative means change is due. */
    @Column(name = "balance_due", nullable = false, precision = 14, scale = 2)
    private BigDecimal balanceDue = BigDecimal.ZERO;

    @Column(name = "cost_of_goods", nullable = false, precision = 14, scale = 2)
    private BigDecimal costOfGoods = BigDecimal.ZERO;

    @Column(name = "note")
    private String note;

    @Column(name = "billed_at", nullable = false)
    private Instant billedAt = Instant.now();

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason")
    private String voidReason;

    @Column(name = "source_cart_id")
    private UUID sourceCartId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineNumber ASC")
    private List<BillLine> lines = new ArrayList<>();

    public void addLine(BillLine line) {
        line.setBill(this);
        lines.add(line);
    }

    public boolean isVoided() {
        return status == BillStatus.VOIDED || voidedAt != null;
    }
}
