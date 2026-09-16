package com.possaas.repairs.dto;

import com.possaas.repairs.domain.RepairLineType;
import com.possaas.repairs.domain.RepairOrderStatus;
import com.possaas.sales.domain.DiscountType;
import com.possaas.sales.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class RepairDtos {

    private RepairDtos() {
    }

    public record CreateRepairRequest(
            UUID customerId,
            @NotBlank @Size(max = 160) String customerName,
            @Size(max = 32) String customerPhone,
            @Size(max = 80) String deviceType,
            @Size(max = 80) String deviceBrand,
            @Size(max = 120) String deviceModel,
            @Size(max = 120) String deviceSerial,
            @Size(max = 160) String repairType,
            String reportedFault,
            String diagnosis,
            List<String> deviceConditions,
            List<String> borrowedItems,
            String accessoriesNote,
            @Size(max = 120) String devicePasscode,
            BigDecimal serviceCharge,
            BigDecimal estimatedCost,
            Short warrantyDays,
            Instant promisedAt,
            BigDecimal advancePaid,
            UUID technicianId,
            String note,
            String idempotencyKey,
            @Valid List<RepairLineRequest> lines
    ) {
    }

    public record UpdateRepairRequest(
            UUID customerId,
            @Size(max = 160) String customerName,
            @Size(max = 32) String customerPhone,
            @Size(max = 80) String deviceType,
            @Size(max = 80) String deviceBrand,
            @Size(max = 120) String deviceModel,
            @Size(max = 120) String deviceSerial,
            @Size(max = 160) String repairType,
            String reportedFault,
            String diagnosis,
            List<String> deviceConditions,
            List<String> borrowedItems,
            String accessoriesNote,
            @Size(max = 120) String devicePasscode,
            BigDecimal serviceCharge,
            BigDecimal discountAmount,
            BigDecimal estimatedCost,
            Short warrantyDays,
            Instant promisedAt,
            UUID technicianId,
            String note
    ) {
    }

    public record RepairLineRequest(
            RepairLineType lineType,
            UUID itemId,
            @NotBlank @Size(max = 200) String description,
            @NotNull @DecimalMin("0.001") BigDecimal quantity,
            BigDecimal unitPrice,
            DiscountType discountType,
            BigDecimal discountInput,
            UUID taxRateId,
            String warrantyLabel,
            List<UUID> serialIds
    ) {
    }

    public record TransitionRequest(
            @NotNull RepairOrderStatus status,
            String note,
            String cancelReason
    ) {
    }

    public record PaymentRequest(
            @NotNull PaymentMethod method,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            BigDecimal tenderedAmount,
            String reference,
            String cardLast4,
            String bankName,
            String chequeNumber,
            LocalDate chequeDate,
            String note
    ) {
    }

    public record RefundRequest(
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotNull PaymentMethod method,
            String reason,
            boolean restoreParts
    ) {
    }

    public record RepairLineResponse(
            UUID id,
            short lineNumber,
            RepairLineType lineType,
            UUID itemId,
            String itemSku,
            String description,
            BigDecimal quantity,
            BigDecimal unitCost,
            BigDecimal unitPrice,
            BigDecimal grossAmount,
            DiscountType discountType,
            BigDecimal discountInput,
            BigDecimal discountAmount,
            BigDecimal netAmount,
            BigDecimal taxRatePercent,
            BigDecimal taxAmount,
            BigDecimal lineTotal,
            String warrantyLabel,
            List<RepairLineSerialResponse> serials
    ) {
    }

    public record RepairLineSerialResponse(
            UUID id,
            UUID itemSerialId,
            String serialNumber
    ) {
    }

    public record RepairHistoryResponse(
            UUID id,
            RepairOrderStatus fromStatus,
            RepairOrderStatus toStatus,
            String note,
            Instant changedAt,
            UUID changedBy
    ) {
    }

    public record RepairResponse(
            UUID id,
            String repairNumber,
            RepairOrderStatus status,
            UUID outletId,
            UUID customerId,
            String customerName,
            String customerPhone,
            String deviceType,
            String deviceBrand,
            String deviceModel,
            String deviceSerial,
            String repairType,
            String reportedFault,
            String diagnosis,
            List<String> deviceConditions,
            List<String> borrowedItems,
            String accessoriesNote,
            String currency,
            BigDecimal serviceCharge,
            BigDecimal partsSubtotal,
            BigDecimal lineDiscountTotal,
            BigDecimal discountAmount,
            BigDecimal taxTotal,
            BigDecimal grandTotal,
            BigDecimal advancePaid,
            BigDecimal amountPaid,
            BigDecimal balanceDue,
            BigDecimal partsCost,
            BigDecimal estimatedCost,
            short warrantyDays,
            LocalDate warrantyEndsOn,
            Instant receivedAt,
            Instant promisedAt,
            Instant completedAt,
            Instant deliveredAt,
            Instant cancelledAt,
            String cancelReason,
            UUID technicianId,
            String note,
            List<RepairLineResponse> lines,
            List<RepairHistoryResponse> history,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
