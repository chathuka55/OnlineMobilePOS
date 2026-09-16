package com.possaas.wholesale.service;

import com.possaas.sales.domain.Payment;
import com.possaas.wholesale.domain.CustomerCreditLedger;
import com.possaas.wholesale.domain.WholesaleInvoice;
import com.possaas.wholesale.domain.WholesaleInvoiceLine;
import com.possaas.wholesale.domain.WholesaleInvoiceLineSerial;
import com.possaas.wholesale.dto.WholesaleDtos.CreditLedgerResponse;
import com.possaas.wholesale.dto.WholesaleDtos.InvoiceLineResponse;
import com.possaas.wholesale.dto.WholesaleDtos.InvoiceLineSerialResponse;
import com.possaas.wholesale.dto.WholesaleDtos.InvoiceResponse;
import com.possaas.wholesale.dto.WholesaleDtos.PaymentResponse;

final class WholesaleMapper {

    private WholesaleMapper() {
    }

    static InvoiceResponse toInvoice(WholesaleInvoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getStatus(),
                invoice.getOutletId(),
                invoice.getCustomerId(),
                invoice.getCustomerName(),
                invoice.getCustomerPhone(),
                invoice.getCurrency(),
                invoice.getSubtotal(),
                invoice.getLineDiscountTotal(),
                invoice.getInvoiceDiscountType(),
                invoice.getInvoiceDiscountInput(),
                invoice.getInvoiceDiscountAmount(),
                invoice.getTaxTotal(),
                invoice.getGrandTotal(),
                invoice.getAmountPaid(),
                invoice.getOutstandingAmount(),
                invoice.getCostOfGoods(),
                invoice.getCreditLimitAtIssue(),
                invoice.getPaymentTermsDays(),
                invoice.getDueDate(),
                invoice.getIssuedAt(),
                invoice.getPostedAt(),
                invoice.getSettledAt(),
                invoice.getVoidedAt(),
                invoice.getVoidReason(),
                invoice.getNote(),
                invoice.getLines().stream().map(WholesaleMapper::toLine).toList(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt()
        );
    }

    static InvoiceLineResponse toLine(WholesaleInvoiceLine line) {
        return new InvoiceLineResponse(
                line.getId(),
                line.getLineNumber(),
                line.getItemId(),
                line.getItemSku(),
                line.getItemName(),
                line.getPriceMode(),
                line.getQuantity(),
                line.getUnitCost(),
                line.getUnitPrice(),
                line.getGrossAmount(),
                line.getDiscountType(),
                line.getDiscountInput(),
                line.getDiscountAmount(),
                line.getNetAmount(),
                line.getTaxRatePercent(),
                line.getTaxAmount(),
                line.getLineTotal(),
                line.getWarrantyLabel(),
                line.getSerials().stream().map(WholesaleMapper::toSerial).toList()
        );
    }

    static InvoiceLineSerialResponse toSerial(WholesaleInvoiceLineSerial serial) {
        return new InvoiceLineSerialResponse(
                serial.getId(),
                serial.getItemSerialId(),
                serial.getSerialNumber()
        );
    }

    static CreditLedgerResponse toLedger(CustomerCreditLedger entry) {
        return new CreditLedgerResponse(
                entry.getId(),
                entry.getCustomerId(),
                entry.getEntryType(),
                entry.getAmountDelta(),
                entry.getBalanceAfter(),
                entry.getDocumentType(),
                entry.getDocumentId(),
                entry.getDocumentNumber(),
                entry.getNote(),
                entry.getOccurredAt()
        );
    }

    static PaymentResponse toPayment(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getMethod(),
                payment.getAmount(),
                payment.getChequeNumber(),
                payment.getChequeDate(),
                payment.getChequeStatus(),
                payment.getReference(),
                payment.getReceivedAt(),
                payment.getNote()
        );
    }
}
