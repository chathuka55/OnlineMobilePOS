package com.possaas.sales.service;

import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.money.Money;
import com.possaas.common.tenant.TenantContext;
import com.possaas.sales.domain.Bill;
import com.possaas.sales.domain.BillStatus;
import com.possaas.sales.domain.CreditNote;
import com.possaas.sales.domain.CreditNoteDocumentType;
import com.possaas.sales.domain.CreditNoteRedemption;
import com.possaas.sales.domain.CreditNoteSourceType;
import com.possaas.sales.domain.CreditNoteStatus;
import com.possaas.sales.dto.SalesDtos.ApplyCreditNoteRequest;
import com.possaas.sales.dto.SalesDtos.BillResponse;
import com.possaas.sales.dto.SalesDtos.CreditNoteResponse;
import com.possaas.sales.dto.SalesDtos.IssueCreditNoteRequest;
import com.possaas.sales.repository.BillRepository;
import com.possaas.sales.repository.CreditNoteRedemptionRepository;
import com.possaas.sales.repository.CreditNoteRepository;
import com.possaas.sales.repository.PaymentRepository;
import com.possaas.sales.domain.PaymentDocumentType;
import com.possaas.tenancy.domain.DocumentType;
import com.possaas.tenancy.service.DocumentNumberService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditNoteService {

    private static final List<CreditNoteStatus> OPEN_STATUSES =
            List.of(CreditNoteStatus.ACTIVE, CreditNoteStatus.PARTIALLY_USED);

    private final CreditNoteRepository creditNoteRepository;
    private final CreditNoteRedemptionRepository redemptionRepository;
    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final DocumentNumberService documentNumberService;

    public CreditNoteService(CreditNoteRepository creditNoteRepository,
                             CreditNoteRedemptionRepository redemptionRepository,
                             BillRepository billRepository,
                             PaymentRepository paymentRepository,
                             DocumentNumberService documentNumberService) {
        this.creditNoteRepository = creditNoteRepository;
        this.redemptionRepository = redemptionRepository;
        this.billRepository = billRepository;
        this.paymentRepository = paymentRepository;
        this.documentNumberService = documentNumberService;
    }

    @Transactional
    public CreditNoteResponse issueFromBill(IssueCreditNoteRequest request) {
        Bill bill = billRepository.findByIdWithLines(request.billId())
                .orElseThrow(() -> ApiException.notFound("Bill", request.billId()));
        if (bill.isVoided()) {
            throw ApiException.of(ErrorCode.BILL_ALREADY_VOIDED, "Cannot issue credit against a voided bill");
        }

        BigDecimal amount = Money.of(request.amount());
        if (Money.gt(amount, bill.getGrandTotal())) {
            throw ApiException.of(ErrorCode.REFUND_EXCEEDS_BILL, "Credit amount exceeds bill total");
        }

        CreditNote note = new CreditNote();
        note.setOutletId(bill.getOutletId());
        note.setCreditNoteNumber(documentNumberService.next(DocumentType.CREDIT_NOTE));
        note.setCustomerId(bill.getCustomerId());
        note.setCustomerName(bill.getCustomerName());
        note.setCustomerPhone(bill.getCustomerPhone());
        note.setSourceType(CreditNoteSourceType.BILL);
        note.setSourceId(bill.getId());
        note.setSourceNumber(bill.getBillNumber());
        note.setCurrency(bill.getCurrency());
        note.setIssuedAmount(amount);
        note.setBalanceAmount(amount);
        note.setStatus(CreditNoteStatus.ACTIVE);
        note.setReason(request.reason());
        note.setExpiresOn(request.expiresOn());
        note.setIssuedAt(Instant.now());
        note.setCreatedBy(TenantContext.userIdOrNull());
        return SalesMapper.toCreditNote(creditNoteRepository.save(note));
    }

    @Transactional(readOnly = true)
    public CreditNoteResponse get(UUID id) {
        return SalesMapper.toCreditNote(creditNoteRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("CreditNote", id)));
    }

    @Transactional(readOnly = true)
    public List<CreditNoteResponse> list(UUID customerId, boolean openOnly) {
        List<CreditNote> notes;
        if (customerId != null) {
            notes = openOnly
                    ? creditNoteRepository.findByCustomerIdAndStatusInOrderByIssuedAtDesc(customerId, OPEN_STATUSES)
                    : creditNoteRepository.findByCustomerIdOrderByIssuedAtDesc(customerId);
        } else {
            notes = openOnly
                    ? creditNoteRepository.findByStatusInOrderByIssuedAtDesc(OPEN_STATUSES)
                    : creditNoteRepository.findAllByOrderByIssuedAtDesc();
        }
        return notes.stream().map(SalesMapper::toCreditNote).toList();
    }

    /**
     * Applies remaining credit-note balance to an existing open bill (post-checkout).
     */
    @Transactional
    public BillResponse apply(UUID creditNoteId, ApplyCreditNoteRequest request) {
        CreditNote note = creditNoteRepository.findByIdForUpdate(creditNoteId)
                .orElseThrow(() -> ApiException.notFound("CreditNote", creditNoteId));
        Bill bill = billRepository.findByIdWithLines(request.billId())
                .orElseThrow(() -> ApiException.notFound("Bill", request.billId()));

        if (bill.isVoided() || bill.getStatus() == BillStatus.COMPLETED
                || bill.getStatus() == BillStatus.REFUNDED) {
            throw ApiException.validation("Bill cannot accept further credit applications");
        }
        if (!Money.isPositive(bill.getBalanceDue())) {
            throw ApiException.validation("Bill has no outstanding balance");
        }
        if (note.getExpiresOn() != null && note.getExpiresOn().isBefore(LocalDate.now())) {
            throw ApiException.of(ErrorCode.CREDIT_NOTE_EXPIRED, "Credit note has expired");
        }
        if (!Money.isPositive(note.getBalanceAmount())) {
            throw ApiException.of(ErrorCode.CREDIT_NOTE_EXHAUSTED, "Credit note balance is zero");
        }
        if (note.getCustomerId() != null && bill.getCustomerId() != null
                && !note.getCustomerId().equals(bill.getCustomerId())) {
            throw ApiException.validation("Credit note belongs to a different customer");
        }

        BigDecimal amount = Money.min(Money.of(request.amount()),
                Money.min(note.getBalanceAmount(), bill.getBalanceDue()));
        if (!Money.isPositive(amount)) {
            throw ApiException.of(ErrorCode.CREDIT_NOTE_EXHAUSTED, "Nothing to apply");
        }

        note.setBalanceAmount(Money.subtract(note.getBalanceAmount(), amount));
        note.refreshStatus();
        creditNoteRepository.save(note);

        CreditNoteRedemption redemption = new CreditNoteRedemption();
        redemption.setCreditNoteId(note.getId());
        redemption.setDocumentType(CreditNoteDocumentType.BILL);
        redemption.setDocumentId(bill.getId());
        redemption.setDocumentNumber(bill.getBillNumber());
        redemption.setAmount(amount);
        redemption.setRedeemedAt(Instant.now());
        redemption.setCreatedBy(TenantContext.userIdOrNull());
        redemptionRepository.save(redemption);

        bill.setCreditApplied(Money.add(bill.getCreditApplied(), amount));
        bill.setBalanceDue(Money.subtract(
                Money.subtract(bill.getGrandTotal(), bill.getCreditApplied()),
                bill.getAmountPaid()));
        bill.setStatus(CheckoutService.resolveStatus(
                bill.getBalanceDue(), bill.getAmountPaid(), bill.getCreditApplied()));
        bill.setUpdatedBy(TenantContext.userIdOrNull());
        bill = billRepository.save(bill);

        return SalesMapper.toBill(bill,
                paymentRepository.findByDocumentTypeAndDocumentIdOrderByReceivedAtAsc(
                        PaymentDocumentType.BILL, bill.getId()));
    }

    /** Internal helper used by refund settlement. */
    @Transactional
    public CreditNote issue(CreditNoteSourceType sourceType,
                            UUID sourceId,
                            String sourceNumber,
                            UUID outletId,
                            UUID customerId,
                            String customerName,
                            String customerPhone,
                            String currency,
                            BigDecimal amount,
                            String reason) {
        CreditNote note = new CreditNote();
        note.setOutletId(outletId);
        note.setCreditNoteNumber(documentNumberService.next(DocumentType.CREDIT_NOTE));
        note.setCustomerId(customerId);
        note.setCustomerName(customerName);
        note.setCustomerPhone(customerPhone);
        note.setSourceType(sourceType);
        note.setSourceId(sourceId);
        note.setSourceNumber(sourceNumber);
        note.setCurrency(currency == null ? "LKR" : currency);
        note.setIssuedAmount(Money.of(amount));
        note.setBalanceAmount(Money.of(amount));
        note.setStatus(CreditNoteStatus.ACTIVE);
        note.setReason(reason);
        note.setIssuedAt(Instant.now());
        note.setCreatedBy(TenantContext.userIdOrNull());
        return creditNoteRepository.save(note);
    }
}
