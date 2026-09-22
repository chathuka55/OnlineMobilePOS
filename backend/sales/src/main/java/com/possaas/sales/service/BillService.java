package com.possaas.sales.service;

import com.possaas.catalog.service.StockLedgerService;
import com.possaas.common.api.PageResponse;
import com.possaas.common.audit.AuditService;
import com.possaas.common.audit.AuditSeverity;
import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.money.Money;
import com.possaas.common.tenant.TenantContext;
import com.possaas.crm.repository.CustomerRepository;
import com.possaas.sales.domain.Bill;
import com.possaas.sales.domain.BillLine;
import com.possaas.sales.domain.BillStatus;
import com.possaas.sales.domain.CreditNote;
import com.possaas.sales.domain.CreditNoteDocumentType;
import com.possaas.sales.domain.CreditNoteRedemption;
import com.possaas.sales.domain.Payment;
import com.possaas.sales.domain.PaymentDirection;
import com.possaas.sales.domain.PaymentDocumentType;
import com.possaas.sales.domain.PaymentMethod;
import com.possaas.sales.dto.SalesDtos.BillResponse;
import com.possaas.sales.dto.SalesDtos.BillSummaryResponse;
import com.possaas.sales.dto.SalesDtos.PaymentRequest;
import com.possaas.sales.dto.SalesDtos.VoidBillRequest;
import com.possaas.sales.repository.BillRepository;
import com.possaas.sales.repository.CreditNoteRedemptionRepository;
import com.possaas.sales.repository.CreditNoteRepository;
import com.possaas.sales.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillService {

    private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T23:59:59Z");

    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final CreditNoteRedemptionRepository redemptionRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final StockLedgerService stockLedgerService;
    private final CustomerRepository customerRepository;
    private final AuditService auditService;

    public BillService(BillRepository billRepository,
                       PaymentRepository paymentRepository,
                       CreditNoteRedemptionRepository redemptionRepository,
                       CreditNoteRepository creditNoteRepository,
                       StockLedgerService stockLedgerService,
                       CustomerRepository customerRepository,
                       AuditService auditService) {
        this.billRepository = billRepository;
        this.paymentRepository = paymentRepository;
        this.redemptionRepository = redemptionRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.stockLedgerService = stockLedgerService;
        this.customerRepository = customerRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public BillResponse get(UUID billId) {
        Bill bill = requireWithLines(billId);
        return SalesMapper.toBill(bill, paymentsFor(billId));
    }

    @Transactional(readOnly = true)
    public PageResponse<BillSummaryResponse> search(String q,
                                                    BillStatus status,
                                                    UUID customerId,
                                                    Instant from,
                                                    Instant to,
                                                    Pageable pageable) {
        String query = (q == null || q.isBlank()) ? null : q.trim();
        // PgJDBC can't infer a type for a bare "$n IS NULL" check on an Instant parameter,
        // so a null from/to blows up with "could not determine data type of parameter" -
        // sidestep it by never binding a null Instant at all.
        Instant effectiveFrom = from == null ? Instant.EPOCH : from;
        Instant effectiveTo = to == null ? FAR_FUTURE : to;
        return PageResponse.of(
                billRepository.search(query, status, customerId, effectiveFrom, effectiveTo, pageable),
                SalesMapper::toBillSummary);
    }

    @Transactional
    public BillResponse voidBill(UUID billId, VoidBillRequest request) {
        Bill bill = requireWithLines(billId);
        if (bill.isVoided()) {
            throw ApiException.of(ErrorCode.BILL_ALREADY_VOIDED, "Bill is already voided");
        }
        if (bill.getStatus() == BillStatus.REFUNDED || bill.getStatus() == BillStatus.PARTIALLY_REFUNDED) {
            throw ApiException.of(ErrorCode.BILL_NOT_REFUNDABLE,
                    "Refunded bills cannot be voided; reverse the refund first");
        }

        for (BillLine line : bill.getLines()) {
            BigDecimal restorable = line.getQuantity().subtract(
                    line.getQuantityReturned() == null ? BigDecimal.ZERO : line.getQuantityReturned());
            if (Money.isPositive(restorable)) {
                stockLedgerService.restore(
                        line.getItemId(),
                        restorable,
                        "BILL",
                        bill.getId(),
                        bill.getBillNumber());
            }
        }
        stockLedgerService.returnSerialsByBill(bill.getId());

        reverseCreditRedemptions(bill);
        reversePayments(bill);

        if (bill.getCustomerId() != null && Money.isPositive(bill.getBalanceDue())) {
            BigDecimal voidedBalanceDue = bill.getBalanceDue();
            BigDecimal voidedGrandTotal = bill.getGrandTotal();
            customerRepository.findById(bill.getCustomerId()).ifPresent(customer -> {
                customer.setOutstandingAmount(
                        Money.max(Money.subtract(customer.getOutstandingAmount(), voidedBalanceDue), Money.ZERO));
                customer.setLifetimeSales(
                        Money.max(Money.subtract(customer.getLifetimeSales(), voidedGrandTotal), Money.ZERO));
                customerRepository.save(customer);
            });
        }

        bill.setStatus(BillStatus.VOIDED);
        bill.setVoidedAt(Instant.now());
        bill.setVoidReason(request != null ? request.reason() : null);
        bill.setUpdatedBy(TenantContext.userIdOrNull());
        bill.setBalanceDue(Money.ZERO);
        bill = billRepository.save(bill);

        auditService.record("BILL", bill.getId(), bill.getBillNumber(), "VOID",
                AuditSeverity.WARN,
                "Voided bill " + bill.getBillNumber()
                        + (request != null && request.reason() != null ? ": " + request.reason() : ""),
                java.util.Map.of("status", "VOIDED"), null);

        return SalesMapper.toBill(bill, paymentsFor(bill.getId()));
    }

    /**
     * Collects a top-up payment on an already-checked-out bill (e.g. layaway
     * or a "pay later" sale). Checkout only records payments at creation time;
     * this is the equivalent of repairs'/wholesale's repeatable collect endpoint.
     */
    @Transactional
    public BillResponse collectPayment(UUID billId, PaymentRequest request) {
        Bill bill = requireWithLines(billId);
        if (bill.isVoided() || bill.getStatus() == BillStatus.REFUNDED
                || bill.getStatus() == BillStatus.PARTIALLY_REFUNDED) {
            throw ApiException.of(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot collect payment on a voided or refunded bill");
        }
        if (!Money.isPositive(bill.getBalanceDue())) {
            throw ApiException.of(ErrorCode.PAYMENT_EXCEEDS_BALANCE, "Bill has no balance due");
        }
        if (request.method() == PaymentMethod.CHEQUE
                && (request.chequeNumber() == null || request.chequeDate() == null)) {
            throw ApiException.validation("Cheque payments require chequeNumber and chequeDate");
        }

        BigDecimal applied = Money.min(Money.of(request.amount()), bill.getBalanceDue());

        Payment payment = new Payment();
        payment.setOutletId(bill.getOutletId());
        payment.setDocumentType(PaymentDocumentType.BILL);
        payment.setDocumentId(bill.getId());
        payment.setCustomerId(bill.getCustomerId());
        payment.setMethod(request.method());
        payment.setDirection(PaymentDirection.IN);
        payment.setAmount(applied);
        payment.setCurrency(bill.getCurrency());
        payment.setTenderedAmount(request.tenderedAmount() != null ? Money.of(request.tenderedAmount()) : null);
        payment.setReference(request.reference());
        payment.setCardLast4(request.cardLast4());
        payment.setBankName(request.bankName());
        payment.setChequeNumber(request.chequeNumber());
        payment.setChequeDate(request.chequeDate());
        if (request.method() == PaymentMethod.CHEQUE) {
            payment.setChequeStatus("PENDING");
        }
        payment.setNote(request.note());
        payment.setIdempotencyKey(request.idempotencyKey());
        payment.setReceivedAt(Instant.now());
        payment.setCreatedBy(TenantContext.userIdOrNull());
        paymentRepository.save(payment);

        bill.setAmountPaid(Money.add(bill.getAmountPaid(), applied));
        bill.setBalanceDue(Money.max(Money.subtract(bill.getBalanceDue(), applied), Money.ZERO));
        bill.setStatus(CheckoutService.resolveStatus(bill.getBalanceDue(), bill.getAmountPaid(), bill.getCreditApplied()));
        bill.setUpdatedBy(TenantContext.userIdOrNull());

        if (bill.getCustomerId() != null) {
            customerRepository.findById(bill.getCustomerId()).ifPresent(customer -> {
                customer.setOutstandingAmount(
                        Money.max(Money.subtract(customer.getOutstandingAmount(), applied), Money.ZERO));
                customerRepository.save(customer);
            });
        }

        bill = billRepository.save(bill);

        auditService.record("BILL", bill.getId(), bill.getBillNumber(), "PAYMENT",
                AuditSeverity.INFO,
                "Collected " + applied + " on bill " + bill.getBillNumber(),
                java.util.Map.of("amount", applied, "method", request.method().name()), null);

        return SalesMapper.toBill(bill, paymentsFor(bill.getId()));
    }

    private void reverseCreditRedemptions(Bill bill) {
        List<CreditNoteRedemption> redemptions = redemptionRepository
                .findByDocumentTypeAndDocumentIdAndReversedAtIsNull(
                        CreditNoteDocumentType.BILL, bill.getId());
        Instant now = Instant.now();
        for (CreditNoteRedemption redemption : redemptions) {
            CreditNote note = creditNoteRepository.findByIdForUpdate(redemption.getCreditNoteId())
                    .orElseThrow(() -> ApiException.notFound("CreditNote", redemption.getCreditNoteId()));
            note.setBalanceAmount(Money.add(note.getBalanceAmount(), redemption.getAmount()));
            note.refreshStatus();
            creditNoteRepository.save(note);
            redemption.setReversedAt(now);
            redemptionRepository.save(redemption);
        }
    }

    private void reversePayments(Bill bill) {
        Instant now = Instant.now();
        for (Payment payment : paymentsFor(bill.getId())) {
            if (payment.getReversedAt() == null) {
                payment.setReversedAt(now);
                payment.setReversalReason("Bill voided");
                paymentRepository.save(payment);
            }
        }
    }

    Bill requireWithLines(UUID billId) {
        return billRepository.findByIdWithLines(billId)
                .orElseThrow(() -> ApiException.notFound("Bill", billId));
    }

    private List<Payment> paymentsFor(UUID billId) {
        return paymentRepository.findByDocumentTypeAndDocumentIdOrderByReceivedAtAsc(
                PaymentDocumentType.BILL, billId);
    }
}
