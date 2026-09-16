package com.possaas.quotations.service;

import com.possaas.quotations.domain.Quotation;
import com.possaas.quotations.domain.QuotationLine;
import com.possaas.quotations.dto.QuotationDtos.QuotationLineResponse;
import com.possaas.quotations.dto.QuotationDtos.QuotationResponse;

final class QuotationMapper {

    private QuotationMapper() {
    }

    static QuotationResponse toResponse(Quotation quotation) {
        return new QuotationResponse(
                quotation.getId(),
                quotation.getQuotationNumber(),
                quotation.getStatus(),
                quotation.getOutletId(),
                quotation.getCustomerId(),
                quotation.getCustomerName(),
                quotation.getCustomerPhone(),
                quotation.getCustomerEmail(),
                quotation.getCurrency(),
                quotation.getPriceMode(),
                quotation.getSubtotal(),
                quotation.getLineDiscountTotal(),
                quotation.getQuoteDiscountType(),
                quotation.getQuoteDiscountInput(),
                quotation.getQuoteDiscountAmount(),
                quotation.getTaxTotal(),
                quotation.getGrandTotal(),
                quotation.getQuotedAt(),
                quotation.getValidUntil(),
                quotation.getSentAt(),
                quotation.getAcceptedAt(),
                quotation.getRejectedAt(),
                quotation.getConvertedAt(),
                quotation.getConvertedBillId(),
                quotation.getTerms(),
                quotation.getNote(),
                quotation.getLines().stream().map(QuotationMapper::toLine).toList(),
                quotation.getCreatedAt(),
                quotation.getUpdatedAt()
        );
    }

    static QuotationLineResponse toLine(QuotationLine line) {
        return new QuotationLineResponse(
                line.getId(),
                line.getLineNumber(),
                line.getItemId(),
                line.getItemSku(),
                line.getItemName(),
                line.getDescription(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.getGrossAmount(),
                line.getDiscountType(),
                line.getDiscountInput(),
                line.getDiscountAmount(),
                line.getNetAmount(),
                line.getTaxRatePercent(),
                line.getTaxAmount(),
                line.getLineTotal(),
                line.getWarrantyLabel()
        );
    }
}
