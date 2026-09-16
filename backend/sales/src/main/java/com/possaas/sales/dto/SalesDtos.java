package com.possaas.sales.dto;

import com.possaas.sales.domain.BillChannel;
import com.possaas.sales.domain.BillStatus;
import com.possaas.sales.domain.CartStatus;
import com.possaas.sales.domain.CreditNoteSourceType;
import com.possaas.sales.domain.CreditNoteStatus;
import com.possaas.sales.domain.DiscountType;
import com.possaas.sales.domain.PaymentMethod;
import com.possaas.sales.domain.PriceMode;
import com.possaas.sales.domain.RefundSettlement;
import com.possaas.sales.domain.RefundType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SalesDtos {

    private SalesDtos() {
    }

    // --- Cart ----------------------------------------------------------------

    public record CreateCartRequest(
            UUID customerId,
            String customerName,
            PriceMode priceMode,
            String label,
            String note
    ) {
    }

    public record UpdateCartRequest(
            UUID customerId,
            String customerName,
            PriceMode priceMode,
            String label,
            String note
    ) {
    }

    public record CartLineRequest(
            @NotNull UUID itemId,
            @NotNull @DecimalMin("0.001") BigDecimal quantity,
            BigDecimal unitPrice,
            DiscountType discountType,
            BigDecimal discountInput,
            UUID taxRateId,
            String warrantyLabel,
            String description,
            List<UUID> serialIds
    ) {
    }

    public record HoldCartRequest(String label) {
    }

    public record CartResponse(
            UUID id,
            CartStatus status,
            UUID outletId,
            UUID customerId,
            String customerName,
            PriceMode priceMode,
            String label,
            String note,
            Instant heldAt,
            UUID convertedBillId,
            List<CartLineResponse> lines,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CartLineResponse(
            UUID id,
            short lineNumber,
            UUID itemId,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            DiscountType discountType,
            BigDecimal discountInput,
            UUID taxRateId,
            String warrantyLabel,
            List<UUID> serialIds
    ) {
    }

    // --- Checkout / Bill -----------------------------------------------------

    public record CheckoutRequest(
            UUID cartId,
            @Valid List<CheckoutLineRequest> lines,
            @Valid List<PaymentRequest> payments,
            @Valid List<CreditNoteApplicationRequest> creditNoteApplications,
            String idempotencyKey,
            UUID customerId,
            String customerName,
            String customerPhone,
            PriceMode priceMode,
            BillChannel channel,
            DiscountType billDiscountType,
            BigDecimal billDiscountInput,
            String note,
            String deviceId,
            LocalDate dueDate
    ) {
    }

    public record CheckoutLineRequest(
            @NotNull UUID itemId,
            @NotNull @DecimalMin("0.001") BigDecimal quantity,
            @NotNull BigDecimal unitPrice,
            DiscountType discountType,
            BigDecimal discountInput,
            UUID taxRateId,
            String warrantyLabel,
            List<UUID> serialIds
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
            String note,
            String idempotencyKey
    ) {
    }

    public record CreditNoteApplicationRequest(
            @NotNull UUID creditNoteId,
            @NotNull @DecimalMin("0.01") BigDecimal amount
    ) {
    }

    public record VoidBillRequest(String reason) {
    }

    public record BillResponse(
            UUID id,
            String billNumber,
            BillStatus status,
            BillChannel channel,
            PriceMode priceMode,
            UUID outletId,
            UUID customerId,
            String customerName,
            String customerPhone,
            String currency,
            BigDecimal subtotal,
            BigDecimal lineDiscountTotal,
            DiscountType billDiscountType,
            BigDecimal billDiscountInput,
            BigDecimal billDiscountAmount,
            BigDecimal taxTotal,
            BigDecimal roundingAdjustment,
            BigDecimal grandTotal,
            BigDecimal creditApplied,
            BigDecimal amountPaid,
            BigDecimal balanceDue,
            BigDecimal costOfGoods,
            String note,
            Instant billedAt,
            LocalDate dueDate,
            Instant voidedAt,
            String voidReason,
            UUID sourceCartId,
            String idempotencyKey,
            List<BillLineResponse> lines,
            List<PaymentResponse> payments
    ) {
    }

    public record BillLineResponse(
            UUID id,
            short lineNumber,
            UUID itemId,
            String itemSku,
            String itemName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal unitCost,
            BigDecimal grossAmount,
            DiscountType discountType,
            BigDecimal discountInput,
            BigDecimal discountAmount,
            BigDecimal netAmount,
            BigDecimal taxRatePercent,
            BigDecimal taxAmount,
            boolean taxInclusive,
            BigDecimal lineTotal,
            String warrantyLabel,
            short warrantyMonths,
            BigDecimal quantityReturned,
            List<BillLineSerialResponse> serials
    ) {
    }

    public record BillLineSerialResponse(
            UUID id,
            UUID itemSerialId,
            String serialNumber,
            Instant returnedAt
    ) {
    }

    public record PaymentResponse(
            UUID id,
            PaymentMethod method,
            BigDecimal amount,
            BigDecimal tenderedAmount,
            BigDecimal changeAmount,
            String reference,
            String cardLast4,
            String bankName,
            String chequeNumber,
            LocalDate chequeDate,
            Instant receivedAt,
            Instant reversedAt
    ) {
    }

    public record BillSummaryResponse(
            UUID id,
            String billNumber,
            BillStatus status,
            String customerName,
            BigDecimal grandTotal,
            BigDecimal amountPaid,
            BigDecimal balanceDue,
            Instant billedAt
    ) {
    }

    // --- Credit notes --------------------------------------------------------

    public record IssueCreditNoteRequest(
            @NotNull UUID billId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            String reason,
            LocalDate expiresOn
    ) {
    }

    public record ApplyCreditNoteRequest(
            @NotNull UUID billId,
            @NotNull @DecimalMin("0.01") BigDecimal amount
    ) {
    }

    public record CreditNoteResponse(
            UUID id,
            String creditNoteNumber,
            CreditNoteStatus status,
            CreditNoteSourceType sourceType,
            UUID sourceId,
            String sourceNumber,
            UUID customerId,
            String customerName,
            BigDecimal issuedAmount,
            BigDecimal balanceAmount,
            String reason,
            LocalDate expiresOn,
            Instant issuedAt
    ) {
    }

    // --- Refunds -------------------------------------------------------------

    public record CreateRefundRequest(
            @NotNull UUID billId,
            RefundType refundType,
            RefundSettlement settlement,
            Boolean restock,
            String reason,
            String note,
            String idempotencyKey,
            @Valid List<RefundLineRequest> lines
    ) {
    }

    public record RefundLineRequest(
            @NotNull UUID billLineId,
            @NotNull @DecimalMin("0.001") BigDecimal quantity,
            List<UUID> serialIds,
            String conditionNote
    ) {
    }

    public record RefundResponse(
            UUID id,
            String refundNumber,
            UUID sourceId,
            String sourceNumber,
            RefundType refundScope,
            RefundSettlement settlement,
            BigDecimal refundAmount,
            boolean restock,
            String reason,
            UUID creditNoteId,
            Instant refundedAt,
            List<RefundLineResponse> lines
    ) {
    }

    public record RefundLineResponse(
            UUID id,
            UUID billLineId,
            UUID itemId,
            String itemName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            boolean restocked
    ) {
    }
}
