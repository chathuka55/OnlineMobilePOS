package com.possaas.quotations.dto;

import com.possaas.quotations.domain.QuotationStatus;
import com.possaas.sales.domain.DiscountType;
import com.possaas.sales.domain.PriceMode;
import com.possaas.sales.dto.SalesDtos.CartResponse;
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

public final class QuotationDtos {

    private QuotationDtos() {
    }

    public record CreateQuotationRequest(
            UUID customerId,
            @NotBlank @Size(max = 160) String customerName,
            @Size(max = 32) String customerPhone,
            String customerEmail,
            PriceMode priceMode,
            LocalDate validUntil,
            DiscountType quoteDiscountType,
            BigDecimal quoteDiscountInput,
            String terms,
            String note,
            @Valid List<QuotationLineRequest> lines
    ) {
    }

    public record UpdateQuotationRequest(
            UUID customerId,
            @Size(max = 160) String customerName,
            @Size(max = 32) String customerPhone,
            String customerEmail,
            PriceMode priceMode,
            LocalDate validUntil,
            DiscountType quoteDiscountType,
            BigDecimal quoteDiscountInput,
            String terms,
            String note
    ) {
    }

    public record QuotationLineRequest(
            UUID itemId,
            @NotBlank @Size(max = 200) String itemName,
            String description,
            String itemSku,
            @NotNull @DecimalMin("0.001") BigDecimal quantity,
            @NotNull BigDecimal unitPrice,
            DiscountType discountType,
            BigDecimal discountInput,
            UUID taxRateId,
            String warrantyLabel
    ) {
    }

    public record TransitionRequest(
            @NotNull QuotationStatus status,
            String note
    ) {
    }

    public record QuotationLineResponse(
            UUID id,
            short lineNumber,
            UUID itemId,
            String itemSku,
            String itemName,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal grossAmount,
            DiscountType discountType,
            BigDecimal discountInput,
            BigDecimal discountAmount,
            BigDecimal netAmount,
            BigDecimal taxRatePercent,
            BigDecimal taxAmount,
            BigDecimal lineTotal,
            String warrantyLabel
    ) {
    }

    public record QuotationResponse(
            UUID id,
            String quotationNumber,
            QuotationStatus status,
            UUID outletId,
            UUID customerId,
            String customerName,
            String customerPhone,
            String customerEmail,
            String currency,
            PriceMode priceMode,
            BigDecimal subtotal,
            BigDecimal lineDiscountTotal,
            DiscountType quoteDiscountType,
            BigDecimal quoteDiscountInput,
            BigDecimal quoteDiscountAmount,
            BigDecimal taxTotal,
            BigDecimal grandTotal,
            Instant quotedAt,
            LocalDate validUntil,
            Instant sentAt,
            Instant acceptedAt,
            Instant rejectedAt,
            Instant convertedAt,
            UUID convertedBillId,
            String terms,
            String note,
            List<QuotationLineResponse> lines,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    /** Print-ready document view (same payload; kept distinct for API clarity). */
    public record QuotationPrintResponse(
            QuotationResponse quotation
    ) {
    }

    public record ConvertResponse(
            QuotationResponse quotation,
            CartResponse cart
    ) {
    }
}
