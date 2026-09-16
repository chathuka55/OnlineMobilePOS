package com.possaas.wholesale.dto;

import com.possaas.sales.domain.DiscountType;
import com.possaas.sales.domain.PaymentMethod;
import com.possaas.wholesale.domain.CreditLedgerEntryType;
import com.possaas.wholesale.domain.WholesaleInvoiceStatus;
import com.possaas.wholesale.domain.WholesalePriceMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class WholesaleDtos {

    private WholesaleDtos() {
    }

    public record CreateInvoiceRequest(
            @NotNull UUID customerId,
            String customerName,
            String customerPhone,
            Short paymentTermsDays,
            LocalDate dueDate,
            DiscountType invoiceDiscountType,
            BigDecimal invoiceDiscountInput,
            String note,
            String idempotencyKey,
            @Valid List<InvoiceLineRequest> lines
    ) {
    }

    public record UpdateInvoiceRequest(
            Short paymentTermsDays,
            LocalDate dueDate,
            DiscountType invoiceDiscountType,
            BigDecimal invoiceDiscountInput,
            String note
    ) {
    }

    public record InvoiceLineRequest(
            @NotNull UUID itemId,
            @NotNull @DecimalMin("0.001") BigDecimal quantity,
            BigDecimal unitPrice,
            WholesalePriceMode priceMode,
            DiscountType discountType,
            BigDecimal discountInput,
            UUID taxRateId,
            String warrantyLabel,
            List<UUID> serialIds
    ) {
    }

    public record CollectionRequest(
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

    public record ClearChequeRequest(
            @NotNull String chequeStatus,
            String note
    ) {
    }

    public record VoidRequest(String reason) {
    }

    public record InvoiceLineSerialResponse(
            UUID id,
            UUID itemSerialId,
            String serialNumber
    ) {
    }

    public record InvoiceLineResponse(
            UUID id,
            short lineNumber,
            UUID itemId,
            String itemSku,
            String itemName,
            WholesalePriceMode priceMode,
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
            List<InvoiceLineSerialResponse> serials
    ) {
    }

    public record InvoiceResponse(
            UUID id,
            String invoiceNumber,
            WholesaleInvoiceStatus status,
            UUID outletId,
            UUID customerId,
            String customerName,
            String customerPhone,
            String currency,
            BigDecimal subtotal,
            BigDecimal lineDiscountTotal,
            DiscountType invoiceDiscountType,
            BigDecimal invoiceDiscountInput,
            BigDecimal invoiceDiscountAmount,
            BigDecimal taxTotal,
            BigDecimal grandTotal,
            BigDecimal amountPaid,
            BigDecimal outstandingAmount,
            BigDecimal costOfGoods,
            BigDecimal creditLimitAtIssue,
            short paymentTermsDays,
            LocalDate dueDate,
            Instant issuedAt,
            Instant postedAt,
            Instant settledAt,
            Instant voidedAt,
            String voidReason,
            String note,
            List<InvoiceLineResponse> lines,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CreditLedgerResponse(
            UUID id,
            UUID customerId,
            CreditLedgerEntryType entryType,
            BigDecimal amountDelta,
            BigDecimal balanceAfter,
            String documentType,
            UUID documentId,
            String documentNumber,
            String note,
            Instant occurredAt
    ) {
    }

    public record PaymentResponse(
            UUID id,
            PaymentMethod method,
            BigDecimal amount,
            String chequeNumber,
            LocalDate chequeDate,
            String chequeStatus,
            String reference,
            Instant receivedAt,
            String note
    ) {
    }
}
