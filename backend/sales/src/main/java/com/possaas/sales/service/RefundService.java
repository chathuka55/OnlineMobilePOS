package com.possaas.sales.service;

import com.possaas.catalog.service.StockLedgerService;
import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.money.Money;
import com.possaas.common.tenant.TenantContext;
import com.possaas.sales.domain.Bill;
import com.possaas.sales.domain.BillLine;
import com.possaas.sales.domain.BillLineSerial;
import com.possaas.sales.domain.BillStatus;
import com.possaas.sales.domain.CreditNote;
import com.possaas.sales.domain.CreditNoteSourceType;
import com.possaas.sales.domain.Payment;
import com.possaas.sales.domain.PaymentDirection;
import com.possaas.sales.domain.PaymentDocumentType;
import com.possaas.sales.domain.PaymentMethod;
import com.possaas.sales.domain.Refund;
import com.possaas.sales.domain.RefundLine;
import com.possaas.sales.domain.RefundSettlement;
import com.possaas.sales.domain.RefundSourceType;
import com.possaas.sales.domain.RefundType;
import com.possaas.sales.dto.SalesDtos.CreateRefundRequest;
import com.possaas.sales.dto.SalesDtos.RefundLineRequest;
import com.possaas.sales.dto.SalesDtos.RefundResponse;
import com.possaas.sales.repository.BillRepository;
import com.possaas.sales.repository.PaymentRepository;
import com.possaas.sales.repository.RefundRepository;
import com.possaas.tenancy.domain.DocumentType;
import com.possaas.tenancy.service.DocumentNumberService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundService {

    private final RefundRepository refundRepository;
    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final DocumentNumberService documentNumberService;
    private final StockLedgerService stockLedgerService;
    private final CreditNoteService creditNoteService;

    public RefundService(RefundRepository refundRepository,
                         BillRepository billRepository,
                         PaymentRepository paymentRepository,
                         DocumentNumberService documentNumberService,
                         StockLedgerService stockLedgerService,
                         CreditNoteService creditNoteService) {
        this.refundRepository = refundRepository;
        this.billRepository = billRepository;
        this.paymentRepository = paymentRepository;
        this.documentNumberService = documentNumberService;
        this.stockLedgerService = stockLedgerService;
        this.creditNoteService = creditNoteService;
    }

    @Transactional
    public RefundResponse create(CreateRefundRequest request) {
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            var existing = refundRepository.findByIdempotencyKey(request.idempotencyKey().trim());
            if (existing.isPresent()) {
                return SalesMapper.toRefund(refundRepository.findByIdWithLines(existing.get().getId())
                        .orElse(existing.get()));
            }
        }

        Bill bill = billRepository.findByIdWithLines(request.billId())
                .orElseThrow(() -> ApiException.notFound("Bill", request.billId()));
        if (bill.isVoided()) {
            throw ApiException.of(ErrorCode.BILL_ALREADY_VOIDED, "Cannot refund a voided bill");
        }
        if (bill.getStatus() == BillStatus.REFUNDED) {
            throw ApiException.of(ErrorCode.BILL_NOT_REFUNDABLE, "Bill is already fully refunded");
        }

        Map<UUID, BillLine> linesById = new HashMap<>();
        for (BillLine line : bill.getLines()) {
            linesById.put(line.getId(), line);
        }

        RefundType scope = request.refundType() == null ? RefundType.PARTIAL : request.refundType();
        boolean restock = request.restock() == null || request.restock();
        List<RefundLineRequest> lineRequests = request.lines();

        if (scope == RefundType.FULL) {
            lineRequests = bill.getLines().stream()
                    .filter(l -> Money.isPositive(l.remainingQuantity()))
                    .map(l -> new RefundLineRequest(
                            l.getId(), l.remainingQuantity(),
                            l.getSerials().stream()
                                    .filter(s -> s.getReturnedAt() == null)
                                    .map(BillLineSerial::getItemSerialId)
                                    .toList(),
                            null))
                    .toList();
        }

        if (lineRequests == null || lineRequests.isEmpty()) {
            throw ApiException.validation("Refund requires at least one line");
        }

        Refund refund = new Refund();
        refund.setOutletId(bill.getOutletId());
        refund.setRefundNumber(documentNumberService.next(DocumentType.REFUND));
        refund.setSourceType(RefundSourceType.BILL);
        refund.setSourceId(bill.getId());
        refund.setSourceNumber(bill.getBillNumber());
        refund.setCustomerId(bill.getCustomerId());
        refund.setCustomerName(bill.getCustomerName());
        refund.setRefundScope(scope);
        refund.setSettlement(request.settlement() == null ? RefundSettlement.CASH : request.settlement());
        refund.setCurrency(bill.getCurrency());
        refund.setRestock(restock);
        refund.setReason(request.reason());
        refund.setNote(request.note());
        refund.setRefundedAt(Instant.now());
        refund.setCreatedBy(TenantContext.userIdOrNull());
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            refund.setIdempotencyKey(request.idempotencyKey().trim());
        }

        BigDecimal refundAmount = Money.ZERO;
        List<UUID> serialsToRestore = new ArrayList<>();

        for (RefundLineRequest lineReq : lineRequests) {
            BillLine billLine = linesById.get(lineReq.billLineId());
            if (billLine == null) {
                throw ApiException.notFound("BillLine", lineReq.billLineId());
            }
            BigDecimal qty = Money.quantity(lineReq.quantity());
            if (Money.gt(qty, billLine.remainingQuantity())) {
                throw ApiException.of(ErrorCode.REFUND_EXCEEDS_BILL,
                                "Refund quantity exceeds remaining sold quantity")
                        .with("billLineId", billLine.getId())
                        .with("remaining", billLine.remainingQuantity())
                        .with("requested", qty);
            }

            // Pro-rate refund value from the original line total
            BigDecimal unitShare = billLine.getLineTotal()
                    .divide(billLine.getQuantity(), Money.SCALE + 4, Money.ROUNDING);
            BigDecimal lineTotal = Money.of(unitShare.multiply(qty));

            RefundLine refundLine = new RefundLine();
            refundLine.setBillLineId(billLine.getId());
            refundLine.setItemId(billLine.getItemId());
            refundLine.setItemName(billLine.getItemName());
            refundLine.setQuantity(qty);
            refundLine.setUnitPrice(billLine.getUnitPrice());
            refundLine.setLineTotal(lineTotal);
            refundLine.setRestocked(restock);
            refundLine.setConditionNote(lineReq.conditionNote());
            refund.addLine(refundLine);

            billLine.setQuantityReturned(Money.quantity(
                    billLine.getQuantityReturned().add(qty)));

            if (restock) {
                stockLedgerService.restore(
                        billLine.getItemId(),
                        qty,
                        "BILL",
                        bill.getId(),
                        bill.getBillNumber());
            }

            if (lineReq.serialIds() != null && !lineReq.serialIds().isEmpty()) {
                Set<UUID> requested = new HashSet<>(lineReq.serialIds());
                Instant now = Instant.now();
                for (BillLineSerial serial : billLine.getSerials()) {
                    if (requested.contains(serial.getItemSerialId()) && serial.getReturnedAt() == null) {
                        serial.setReturnedAt(now);
                        serialsToRestore.add(serial.getItemSerialId());
                    }
                }
            } else if (restock && !billLine.getSerials().isEmpty()) {
                // Full remaining serials on this line when count matches
                long open = billLine.getSerials().stream().filter(s -> s.getReturnedAt() == null).count();
                if (open == qty.intValue()) {
                    Instant now = Instant.now();
                    for (BillLineSerial serial : billLine.getSerials()) {
                        if (serial.getReturnedAt() == null) {
                            serial.setReturnedAt(now);
                            serialsToRestore.add(serial.getItemSerialId());
                        }
                    }
                }
            }

            refundAmount = Money.add(refundAmount, lineTotal);
        }

        if (restock && !serialsToRestore.isEmpty()) {
            stockLedgerService.returnSerials(serialsToRestore);
        }

        refund.setRefundAmount(refundAmount);

        if (refund.getSettlement() == RefundSettlement.CREDIT_NOTE) {
            CreditNote note = creditNoteService.issue(
                    CreditNoteSourceType.BILL,
                    bill.getId(),
                    bill.getBillNumber(),
                    bill.getOutletId(),
                    bill.getCustomerId(),
                    bill.getCustomerName(),
                    bill.getCustomerPhone(),
                    bill.getCurrency(),
                    refundAmount,
                    request.reason());
            refund.setCreditNoteId(note.getId());
        } else {
            Payment outflow = new Payment();
            outflow.setOutletId(bill.getOutletId());
            outflow.setDocumentType(PaymentDocumentType.BILL);
            outflow.setDocumentId(bill.getId());
            outflow.setCustomerId(bill.getCustomerId());
            outflow.setMethod(toPaymentMethod(refund.getSettlement()));
            outflow.setDirection(PaymentDirection.OUT);
            outflow.setAmount(refundAmount);
            outflow.setCurrency(bill.getCurrency());
            outflow.setReceivedAt(Instant.now());
            outflow.setNote("Refund " + refund.getRefundNumber());
            outflow.setCreatedBy(TenantContext.userIdOrNull());
            paymentRepository.save(outflow);
        }

        boolean fullyReturned = bill.getLines().stream()
                .allMatch(l -> l.remainingQuantity().signum() == 0);
        bill.setStatus(fullyReturned ? BillStatus.REFUNDED : BillStatus.PARTIALLY_REFUNDED);
        // Cash/card settlements reduce money retained; credit-note settlements leave amount_paid alone.
        if (refund.getSettlement() != RefundSettlement.CREDIT_NOTE) {
            bill.setAmountPaid(Money.max(Money.subtract(bill.getAmountPaid(), refundAmount), Money.ZERO));
        }
        // Balance is against remaining (non-returned) line value, not the original grand total.
        BigDecimal remainingValue = remainingLineValue(bill);
        bill.setBalanceDue(Money.subtract(
                Money.subtract(remainingValue, bill.getCreditApplied()),
                bill.getAmountPaid()));
        bill.setUpdatedBy(TenantContext.userIdOrNull());
        billRepository.save(bill);

        return SalesMapper.toRefund(refundRepository.save(refund));
    }

    @Transactional(readOnly = true)
    public RefundResponse get(UUID id) {
        return SalesMapper.toRefund(refundRepository.findByIdWithLines(id)
                .orElseThrow(() -> ApiException.notFound("Refund", id)));
    }

    private static PaymentMethod toPaymentMethod(RefundSettlement settlement) {
        return switch (settlement) {
            case CASH -> PaymentMethod.CASH;
            case CARD_REVERSAL -> PaymentMethod.CARD;
            case BANK_TRANSFER -> PaymentMethod.BANK_TRANSFER;
            case CHEQUE -> PaymentMethod.CHEQUE;
            case CREDIT_NOTE -> PaymentMethod.CREDIT_NOTE;
        };
    }

    private static BigDecimal remainingLineValue(Bill bill) {
        BigDecimal total = Money.ZERO;
        for (BillLine line : bill.getLines()) {
            BigDecimal remaining = line.remainingQuantity();
            if (!Money.isPositive(remaining)) {
                continue;
            }
            BigDecimal unitShare = line.getLineTotal()
                    .divide(line.getQuantity(), Money.SCALE + 4, Money.ROUNDING);
            total = Money.add(total, Money.of(unitShare.multiply(remaining)));
        }
        return total;
    }
}
