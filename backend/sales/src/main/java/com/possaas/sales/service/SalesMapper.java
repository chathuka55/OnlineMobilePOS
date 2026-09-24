package com.possaas.sales.service;

import com.possaas.sales.domain.Bill;
import com.possaas.sales.domain.BillLine;
import com.possaas.sales.domain.BillLineSerial;
import com.possaas.sales.domain.Cart;
import com.possaas.sales.domain.CartLine;
import com.possaas.sales.domain.CreditNote;
import com.possaas.sales.domain.Payment;
import com.possaas.sales.domain.Refund;
import com.possaas.sales.domain.RefundLine;
import com.possaas.sales.dto.SalesDtos.BillLineResponse;
import com.possaas.sales.dto.SalesDtos.BillLineSerialResponse;
import com.possaas.sales.dto.SalesDtos.BillResponse;
import com.possaas.sales.dto.SalesDtos.BillSummaryResponse;
import com.possaas.sales.dto.SalesDtos.CartLineResponse;
import com.possaas.sales.dto.SalesDtos.CartResponse;
import com.possaas.sales.dto.SalesDtos.CreditNoteResponse;
import com.possaas.sales.dto.SalesDtos.PaymentResponse;
import com.possaas.sales.dto.SalesDtos.RefundLineResponse;
import com.possaas.sales.dto.SalesDtos.RefundResponse;
import java.util.Arrays;
import java.util.List;

final class SalesMapper {

    private SalesMapper() {
    }

    static CartResponse toCart(Cart cart) {
        List<CartLineResponse> lines = cart.getLines().stream().map(SalesMapper::toCartLine).toList();
        return new CartResponse(
                cart.getId(),
                cart.getStatus(),
                cart.getOutletId(),
                cart.getCustomerId(),
                cart.getCustomerName(),
                cart.getPriceMode(),
                cart.getLabel(),
                cart.getNote(),
                cart.getHeldAt(),
                cart.getConvertedBillId(),
                lines,
                cart.getCreatedAt(),
                cart.getUpdatedAt()
        );
    }

    static CartLineResponse toCartLine(CartLine line) {
        List<java.util.UUID> serials = line.getSerialIds() == null
                ? List.of()
                : Arrays.asList(line.getSerialIds());
        return new CartLineResponse(
                line.getId(),
                line.getLineNumber(),
                line.getItemId(),
                line.getDescription(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.getDiscountType(),
                line.getDiscountInput(),
                line.getTaxRateId(),
                line.getWarrantyLabel(),
                serials
        );
    }

    static BillResponse toBill(Bill bill, List<Payment> payments) {
        return new BillResponse(
                bill.getId(),
                bill.getBillNumber(),
                bill.getStatus(),
                bill.getChannel(),
                bill.getPriceMode(),
                bill.getOutletId(),
                bill.getCustomerId(),
                bill.getCustomerName(),
                bill.getCustomerPhone(),
                bill.getCurrency(),
                bill.getSubtotal(),
                bill.getLineDiscountTotal(),
                bill.getBillDiscountType(),
                bill.getBillDiscountInput(),
                bill.getBillDiscountAmount(),
                bill.getTaxTotal(),
                bill.getRoundingAdjustment(),
                bill.getGrandTotal(),
                bill.getCreditApplied(),
                bill.getAmountPaid(),
                bill.getBalanceDue(),
                bill.getCostOfGoods(),
                bill.getNote(),
                bill.getBilledAt(),
                bill.getDueDate(),
                bill.getVoidedAt(),
                bill.getVoidReason(),
                bill.getSourceCartId(),
                bill.getIdempotencyKey(),
                bill.getLines().stream().map(SalesMapper::toBillLine).toList(),
                payments.stream().map(SalesMapper::toPayment).toList()
        );
    }

    static BillSummaryResponse toBillSummary(Bill bill) {
        return new BillSummaryResponse(
                bill.getId(),
                bill.getBillNumber(),
                bill.getStatus(),
                bill.getCustomerName(),
                bill.getGrandTotal(),
                bill.getAmountPaid(),
                bill.getBalanceDue(),
                bill.getBilledAt()
        );
    }

    static BillLineResponse toBillLine(BillLine line) {
        return new BillLineResponse(
                line.getId(),
                line.getLineNumber(),
                line.getItemId(),
                line.getItemSku(),
                line.getItemName(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.getUnitCost(),
                line.getGrossAmount(),
                line.getDiscountType(),
                line.getDiscountInput(),
                line.getDiscountAmount(),
                line.getAllocatedBillDiscount(),
                line.getNetAmount(),
                line.getTaxRatePercent(),
                line.getTaxAmount(),
                line.isTaxInclusive(),
                line.getLineTotal(),
                line.getWarrantyLabel(),
                line.getWarrantyMonths(),
                line.getQuantityReturned(),
                line.getSerials().stream().map(SalesMapper::toBillLineSerial).toList()
        );
    }

    static BillLineSerialResponse toBillLineSerial(BillLineSerial serial) {
        return new BillLineSerialResponse(
                serial.getId(),
                serial.getItemSerialId(),
                serial.getSerialNumber(),
                serial.getReturnedAt()
        );
    }

    static PaymentResponse toPayment(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getMethod(),
                payment.getAmount(),
                payment.getTenderedAmount(),
                payment.getChangeAmount(),
                payment.getReference(),
                payment.getCardLast4(),
                payment.getBankName(),
                payment.getChequeNumber(),
                payment.getChequeDate(),
                payment.getReceivedAt(),
                payment.getReversedAt()
        );
    }

    static CreditNoteResponse toCreditNote(CreditNote note) {
        return new CreditNoteResponse(
                note.getId(),
                note.getCreditNoteNumber(),
                note.getStatus(),
                note.getSourceType(),
                note.getSourceId(),
                note.getSourceNumber(),
                note.getCustomerId(),
                note.getCustomerName(),
                note.getIssuedAmount(),
                note.getBalanceAmount(),
                note.getReason(),
                note.getExpiresOn(),
                note.getIssuedAt()
        );
    }

    static RefundResponse toRefund(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getRefundNumber(),
                refund.getSourceId(),
                refund.getSourceNumber(),
                refund.getRefundScope(),
                refund.getSettlement(),
                refund.getRefundAmount(),
                refund.isRestock(),
                refund.getReason(),
                refund.getCreditNoteId(),
                refund.getRefundedAt(),
                refund.getLines().stream().map(SalesMapper::toRefundLine).toList()
        );
    }

    static RefundLineResponse toRefundLine(RefundLine line) {
        return new RefundLineResponse(
                line.getId(),
                line.getBillLineId(),
                line.getItemId(),
                line.getItemName(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.getLineTotal(),
                line.isRestocked()
        );
    }
}
