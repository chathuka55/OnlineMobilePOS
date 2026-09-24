package com.possaas.repairs.domain;

import com.possaas.common.jpa.TenantEntity;
import com.possaas.common.money.Money;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "repair_orders")
@Getter
@Setter
@NoArgsConstructor
public class RepairOrder extends TenantEntity {

    @Column(name = "outlet_id", nullable = false)
    private UUID outletId;

    @Column(name = "repair_number", nullable = false)
    private String repairNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RepairOrderStatus status = RepairOrderStatus.RECEIVED;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "device_type")
    private String deviceType;

    @Column(name = "device_brand")
    private String deviceBrand;

    @Column(name = "device_model")
    private String deviceModel;

    @Column(name = "device_serial")
    private String deviceSerial;

    @Column(name = "repair_type")
    private String repairType;

    @Column(name = "reported_fault")
    private String reportedFault;

    @Column(name = "diagnosis")
    private String diagnosis;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "device_conditions", columnDefinition = "text[]", nullable = false)
    private String[] deviceConditions = new String[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "borrowed_items", columnDefinition = "text[]", nullable = false)
    private String[] borrowedItems = new String[0];

    @Column(name = "accessories_note")
    private String accessoriesNote;

    @Column(name = "device_passcode")
    private String devicePasscode;

    @Column(name = "currency", nullable = false)
    private String currency = "LKR";

    @Column(name = "service_charge", nullable = false, precision = 14, scale = 2)
    private BigDecimal serviceCharge = Money.ZERO;

    @Column(name = "parts_subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal partsSubtotal = Money.ZERO;

    @Column(name = "line_discount_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineDiscountTotal = Money.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount = Money.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxTotal = Money.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal grandTotal = Money.ZERO;

    @Column(name = "advance_paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal advancePaid = Money.ZERO;

    @Column(name = "amount_paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountPaid = Money.ZERO;

    @Column(name = "balance_due", nullable = false, precision = 14, scale = 2)
    private BigDecimal balanceDue = Money.ZERO;

    @Column(name = "parts_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal partsCost = Money.ZERO;

    @Column(name = "estimated_cost", precision = 14, scale = 2)
    private BigDecimal estimatedCost;

    @Column(name = "warranty_days", nullable = false)
    private short warrantyDays;

    @Column(name = "warranty_ends_on")
    private LocalDate warrantyEndsOn;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    @Column(name = "promised_at")
    private Instant promisedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Set when parts left stock. QC is blocked until this is set. */
    @Column(name = "parts_consumed_at")
    private Instant partsConsumedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    /** Customer-approved ceiling for a job that grew beyond its estimate. */
    @Column(name = "approved_amount", precision = 14, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "technician_id")
    private UUID technicianId;

    @Column(name = "note")
    private String note;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @OneToMany(mappedBy = "repairOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineNumber ASC")
    private List<RepairLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "repairOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("changedAt ASC")
    private List<RepairHistory> history = new ArrayList<>();

    public void addLine(RepairLine line) {
        line.setRepairOrder(this);
        lines.add(line);
    }

    public void addHistory(RepairHistory entry) {
        entry.setRepairOrder(this);
        history.add(entry);
    }

    public boolean isTerminal() {
        return status == RepairOrderStatus.DELIVERED
                || status == RepairOrderStatus.CANCELLED;
    }

    public boolean isEditable() {
        return status != RepairOrderStatus.DELIVERED
                && status != RepairOrderStatus.CANCELLED
                && status != RepairOrderStatus.COMPLETED;
    }

    public boolean partsWereDeducted() {
        return partsConsumedAt != null;
    }

    public boolean hasPartLines() {
        return lines.stream().anyMatch(RepairLine::isPart);
    }

    /**
     * What the customer has agreed to pay: the approved amount once the job has
     * been re-quoted, otherwise the original estimate. Null means no estimate was
     * ever given, so there is nothing to exceed.
     */
    public BigDecimal approvalCeiling() {
        return approvedAmount != null ? approvedAmount : estimatedCost;
    }

    /** True when the job now costs more than the customer has agreed to. */
    public boolean exceedsApprovedAmount() {
        BigDecimal ceiling = approvalCeiling();
        return ceiling != null && grandTotal.compareTo(ceiling) > 0;
    }

    public void recordApproval(BigDecimal amount, UUID userId) {
        this.approvedAmount = amount;
        this.approvedAt = Instant.now();
        this.approvedBy = userId;
    }
}
