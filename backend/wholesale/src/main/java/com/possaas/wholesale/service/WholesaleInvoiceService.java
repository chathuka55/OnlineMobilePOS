package com.possaas.wholesale.service;

import com.possaas.catalog.domain.Item;
import com.possaas.catalog.domain.ItemSerial;
import com.possaas.catalog.domain.SoldDocumentType;
import com.possaas.catalog.repository.ItemRepository;
import com.possaas.catalog.repository.ItemSerialRepository;
import com.possaas.catalog.service.StockLedgerService;
import com.possaas.common.api.PageResponse;
import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.money.Money;
import com.possaas.common.tenant.TenantContext;
import com.possaas.crm.domain.Customer;
import com.possaas.crm.repository.CustomerRepository;
import com.possaas.sales.domain.DiscountType;
import com.possaas.sales.domain.Payment;
import com.possaas.sales.domain.PaymentDirection;
import com.possaas.sales.domain.PaymentDocumentType;
import com.possaas.sales.domain.PaymentMethod;
import com.possaas.sales.repository.PaymentRepository;
import com.possaas.sales.service.SalesPricing;
import com.possaas.tenancy.domain.DocumentType;
import com.possaas.tenancy.domain.TaxRate;
import com.possaas.tenancy.repository.TaxRateRepository;
import com.possaas.tenancy.service.DocumentNumberService;
import com.possaas.wholesale.domain.CreditLedgerEntryType;
import com.possaas.wholesale.domain.CustomerCreditLedger;
import com.possaas.wholesale.domain.WholesaleInvoice;
import com.possaas.wholesale.domain.WholesaleInvoiceLine;
import com.possaas.wholesale.domain.WholesaleInvoiceLineSerial;
import com.possaas.wholesale.domain.WholesaleInvoiceStatus;
import com.possaas.wholesale.domain.WholesalePriceMode;
import com.possaas.wholesale.dto.WholesaleDtos.ClearChequeRequest;
import com.possaas.wholesale.dto.WholesaleDtos.CollectionRequest;
import com.possaas.wholesale.dto.WholesaleDtos.CreateInvoiceRequest;
import com.possaas.wholesale.dto.WholesaleDtos.CreditLedgerResponse;
import com.possaas.wholesale.dto.WholesaleDtos.InvoiceLineRequest;
import com.possaas.wholesale.dto.WholesaleDtos.InvoiceResponse;
import com.possaas.wholesale.dto.WholesaleDtos.PaymentResponse;
import com.possaas.wholesale.dto.WholesaleDtos.UpdateInvoiceRequest;
import com.possaas.wholesale.dto.WholesaleDtos.VoidRequest;
import com.possaas.wholesale.repository.CustomerCreditLedgerRepository;
import com.possaas.wholesale.repository.WholesaleInvoiceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WholesaleInvoiceService {

    private static final String DOC_TYPE = "WHOLESALE_INVOICE";
    private static final Set<String> CHEQUE_STATUSES =
            Set.of("PENDING", "DEPOSITED", "CLEARED", "BOUNCED", "CANCELLED");

    private final WholesaleInvoiceRepository invoiceRepository;
    private final CustomerCreditLedgerRepository ledgerRepository;
    private final CustomerRepository customerRepository;
    private final ItemRepository itemRepository;
    private final ItemSerialRepository itemSerialRepository;
    private final TaxRateRepository taxRateRepository;
    private final PaymentRepository paymentRepository;
    private final DocumentNumberService documentNumberService;
    private final StockLedgerService stockLedgerService;

    public WholesaleInvoiceService(WholesaleInvoiceRepository invoiceRepository,
                                   CustomerCreditLedgerRepository ledgerRepository,
                                   CustomerRepository customerRepository,
                                   ItemRepository itemRepository,
                                   ItemSerialRepository itemSerialRepository,
                                   TaxRateRepository taxRateRepository,
                                   PaymentRepository paymentRepository,
                                   DocumentNumberService documentNumberService,
                                   StockLedgerService stockLedgerService) {
        this.invoiceRepository = invoiceRepository;
        this.ledgerRepository = ledgerRepository;
        this.customerRepository = customerRepository;
        this.itemRepository = itemRepository;
        this.itemSerialRepository = itemSerialRepository;
        this.taxRateRepository = taxRateRepository;
        this.paymentRepository = paymentRepository;
        this.documentNumberService = documentNumberService;
        this.stockLedgerService = stockLedgerService;
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> list(String q, WholesaleInvoiceStatus status, UUID customerId,
                                              Pageable pageable) {
        return PageResponse.of(
                invoiceRepository.search(blankToNull(q), status, customerId, pageable)
                        .map(WholesaleMapper::toInvoice));
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> collections(UUID customerId, Pageable pageable) {
        return PageResponse.of(
                invoiceRepository.findOutstanding(customerId, pageable).map(WholesaleMapper::toInvoice));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(UUID id) {
        return WholesaleMapper.toInvoice(requireWithLines(id));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listPayments(UUID invoiceId) {
        requireWithLines(invoiceId);
        return paymentRepository
                .findByDocumentTypeAndDocumentIdOrderByReceivedAtAsc(
                        PaymentDocumentType.WHOLESALE_INVOICE, invoiceId)
                .stream()
                .map(WholesaleMapper::toPayment)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<CreditLedgerResponse> customerLedger(UUID customerId, Pageable pageable) {
        customerRepository.findByIdAndDeletedAtIsNull(customerId)
                .orElseThrow(() -> ApiException.notFound("Customer", customerId));
        return PageResponse.of(
                ledgerRepository.findByCustomerIdOrderByOccurredAtDesc(customerId, pageable)
                        .map(WholesaleMapper::toLedger));
    }

    @Transactional
    public InvoiceResponse create(CreateInvoiceRequest request) {
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            var existing = invoiceRepository.findByIdempotencyKey(request.idempotencyKey().trim());
            if (existing.isPresent()) {
                return WholesaleMapper.toInvoice(requireWithLines(existing.get().getId()));
            }
        }

        Customer customer = customerRepository.findByIdAndDeletedAtIsNull(request.customerId())
                .orElseThrow(() -> ApiException.notFound("Customer", request.customerId()));

        WholesaleInvoice invoice = new WholesaleInvoice();
        invoice.setOutletId(TenantContext.requireOutletId());
        invoice.setInvoiceNumber(documentNumberService.next(DocumentType.WHOLESALE_INVOICE));
        invoice.setStatus(WholesaleInvoiceStatus.DRAFT);
        invoice.setCustomerId(customer.getId());
        invoice.setCustomerName(request.customerName() != null && !request.customerName().isBlank()
                ? request.customerName().trim()
                : customer.getDisplayName());
        invoice.setCustomerPhone(request.customerPhone() != null
                ? request.customerPhone()
                : customer.getPhonePrimary());
        invoice.setPaymentTermsDays(request.paymentTermsDays() == null ? 0 : request.paymentTermsDays());
        invoice.setDueDate(request.dueDate());
        invoice.setInvoiceDiscountType(request.invoiceDiscountType() == null
                ? DiscountType.NONE : request.invoiceDiscountType());
        invoice.setInvoiceDiscountInput(Money.of(request.invoiceDiscountInput()));
        invoice.setNote(blankToNull(request.note()));
        invoice.setIssuedAt(Instant.now());
        invoice.setCreatedBy(TenantContext.userIdOrNull());
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            invoice.setIdempotencyKey(request.idempotencyKey().trim());
        }

        if (request.lines() != null) {
            short n = 1;
            for (InvoiceLineRequest lineRequest : request.lines()) {
                invoice.addLine(buildLine(lineRequest, n++));
            }
        }
        recalculate(invoice);
        return WholesaleMapper.toInvoice(invoiceRepository.save(invoice));
    }

    @Transactional
    public InvoiceResponse update(UUID id, UpdateInvoiceRequest request) {
        WholesaleInvoice invoice = requireWithLines(id);
        requireDraft(invoice);
        if (request.paymentTermsDays() != null) {
            invoice.setPaymentTermsDays(request.paymentTermsDays());
        }
        if (request.dueDate() != null) {
            invoice.setDueDate(request.dueDate());
        }
        if (request.invoiceDiscountType() != null) {
            invoice.setInvoiceDiscountType(request.invoiceDiscountType());
        }
        if (request.invoiceDiscountInput() != null) {
            invoice.setInvoiceDiscountInput(Money.of(request.invoiceDiscountInput()));
        }
        if (request.note() != null) {
            invoice.setNote(blankToNull(request.note()));
        }
        invoice.setUpdatedBy(TenantContext.userIdOrNull());
        recalculate(invoice);
        return WholesaleMapper.toInvoice(invoiceRepository.save(invoice));
    }

    @Transactional
    public InvoiceResponse addLine(UUID id, InvoiceLineRequest request) {
        WholesaleInvoice invoice = requireWithLines(id);
        requireDraft(invoice);
        short next = (short) (invoice.getLines().stream()
                .mapToInt(WholesaleInvoiceLine::getLineNumber).max().orElse(0) + 1);
        invoice.addLine(buildLine(request, next));
        invoice.setUpdatedBy(TenantContext.userIdOrNull());
        recalculate(invoice);
        return WholesaleMapper.toInvoice(invoiceRepository.save(invoice));
    }

    @Transactional
    public InvoiceResponse removeLine(UUID id, UUID lineId) {
        WholesaleInvoice invoice = requireWithLines(id);
        requireDraft(invoice);
        WholesaleInvoiceLine line = invoice.getLines().stream()
                .filter(l -> l.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("WholesaleInvoiceLine", lineId));
        invoice.getLines().remove(line);
        short n = 1;
        for (WholesaleInvoiceLine remaining : invoice.getLines()) {
            remaining.setLineNumber(n++);
        }
        invoice.setUpdatedBy(TenantContext.userIdOrNull());
        recalculate(invoice);
        return WholesaleMapper.toInvoice(invoiceRepository.save(invoice));
    }

    /**
     * Commits the invoice: credit-limit check, stock deduction, customer outstanding + ledger.
     * Stock is never touched while the invoice is still a draft.
     */
    @Transactional
    public InvoiceResponse post(UUID id) {
        WholesaleInvoice invoice = requireWithLines(id);
        requireDraft(invoice);
        if (invoice.getLines().isEmpty()) {
            throw ApiException.validation("Cannot post an invoice with no lines");
        }

        Customer customer = customerRepository.findByIdAndDeletedAtIsNull(invoice.getCustomerId())
                .orElseThrow(() -> ApiException.notFound("Customer", invoice.getCustomerId()));

        recalculate(invoice);
        BigDecimal newOutstanding = invoice.getOutstandingAmount();
        BigDecimal projected = Money.add(customer.getOutstandingAmount(), newOutstanding);
        if (Money.isPositive(projected) && Money.gt(projected, customer.getCreditLimit())) {
            throw ApiException.of(ErrorCode.CREDIT_LIMIT_EXCEEDED,
                            "Customer would exceed their credit limit")
                    .with("creditLimit", customer.getCreditLimit())
                    .with("outstandingAmount", customer.getOutstandingAmount())
                    .with("invoiceOutstanding", newOutstanding)
                    .with("projected", projected);
        }

        for (WholesaleInvoiceLine line : invoice.getLines()) {
            stockLedgerService.sell(
                    line.getItemId(),
                    line.getQuantity(),
                    DOC_TYPE,
                    invoice.getId(),
                    invoice.getInvoiceNumber());
            if (!line.getSerials().isEmpty()) {
                List<UUID> serialIds = line.getSerials().stream()
                        .map(WholesaleInvoiceLineSerial::getItemSerialId)
                        .toList();
                stockLedgerService.markSerialsSold(
                        serialIds, SoldDocumentType.WHOLESALE_INVOICE, invoice.getId(),
                        invoice.getInvoiceNumber());
            }
        }

        invoice.setCreditLimitAtIssue(customer.getCreditLimit());
        if (invoice.getDueDate() == null && invoice.getPaymentTermsDays() > 0) {
            invoice.setDueDate(LocalDate.now().plusDays(invoice.getPaymentTermsDays()));
        }
        invoice.setPostedAt(Instant.now());
        invoice.setStatus(resolveStatus(invoice));
        invoice.setUpdatedBy(TenantContext.userIdOrNull());

        if (Money.isPositive(newOutstanding)) {
            customer.adjustOutstanding(newOutstanding);
            customer.setLifetimeSales(Money.add(customer.getLifetimeSales(), invoice.getGrandTotal()));
            customerRepository.save(customer);
            appendLedger(customer, CreditLedgerEntryType.INVOICE, newOutstanding,
                    DOC_TYPE, invoice.getId(), invoice.getInvoiceNumber(), "Invoice posted");
        } else {
            customer.setLifetimeSales(Money.add(customer.getLifetimeSales(), invoice.getGrandTotal()));
            customerRepository.save(customer);
        }

        return WholesaleMapper.toInvoice(invoiceRepository.save(invoice));
    }

    @Transactional
    public InvoiceResponse collect(UUID id, CollectionRequest request) {
        WholesaleInvoice invoice = requireWithLines(id);
        if (invoice.getStatus() == WholesaleInvoiceStatus.SETTLED) {
            throw ApiException.of(ErrorCode.PAYMENT_EXCEEDS_BALANCE, "Invoice is already settled");
        }
        if (!invoice.isOpen()) {
            throw ApiException.of(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot collect on invoice in status " + invoice.getStatus());
        }
        if (!Money.isPositive(invoice.getOutstandingAmount())) {
            throw ApiException.of(ErrorCode.PAYMENT_EXCEEDS_BALANCE, "Invoice has no outstanding balance");
        }

        BigDecimal applied = Money.min(Money.of(request.amount()), invoice.getOutstandingAmount());
        Payment payment = createPayment(invoice, request, applied);
        paymentRepository.save(payment);

        invoice.setAmountPaid(Money.add(invoice.getAmountPaid(), applied));
        invoice.setOutstandingAmount(Money.subtract(invoice.getGrandTotal(), invoice.getAmountPaid()));
        invoice.setStatus(resolveStatus(invoice));
        if (invoice.getStatus() == WholesaleInvoiceStatus.SETTLED) {
            invoice.setSettledAt(Instant.now());
        }
        invoice.setUpdatedBy(TenantContext.userIdOrNull());

        Customer customer = customerRepository.findByIdAndDeletedAtIsNull(invoice.getCustomerId())
                .orElseThrow(() -> ApiException.notFound("Customer", invoice.getCustomerId()));
        customer.adjustOutstanding(Money.negate(applied));
        if (customer.getOutstandingAmount().signum() < 0) {
            customer.setOutstandingAmount(Money.ZERO);
        }
        customerRepository.save(customer);
        appendLedger(customer, CreditLedgerEntryType.PAYMENT, Money.negate(applied),
                DOC_TYPE, invoice.getId(), invoice.getInvoiceNumber(), "Collection");

        return WholesaleMapper.toInvoice(invoiceRepository.save(invoice));
    }

    @Transactional
    public PaymentResponse clearCheque(UUID invoiceId, UUID paymentId, ClearChequeRequest request) {
        WholesaleInvoice invoice = requireWithLines(invoiceId);
        String status = request.chequeStatus() == null ? "" : request.chequeStatus().trim().toUpperCase();
        if (!CHEQUE_STATUSES.contains(status)) {
            throw ApiException.validation("Invalid cheque status")
                    .with("chequeStatus", request.chequeStatus());
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Payment", paymentId));
        if (payment.getDocumentType() != PaymentDocumentType.WHOLESALE_INVOICE
                || !payment.getDocumentId().equals(invoiceId)) {
            throw ApiException.notFound("Payment", paymentId);
        }
        if (payment.getMethod() != PaymentMethod.CHEQUE) {
            throw ApiException.validation("Payment is not a cheque");
        }

        String previous = payment.getChequeStatus();
        payment.setChequeStatus(status);
        if (request.note() != null) {
            payment.setNote(blankToNull(request.note()));
        }
        paymentRepository.save(payment);

        if ("BOUNCED".equals(status) && !"BOUNCED".equals(previous)) {
            reverseChequePayment(invoice, payment);
        }

        return WholesaleMapper.toPayment(payment);
    }

    @Transactional
    public InvoiceResponse voidInvoice(UUID id, VoidRequest request) {
        WholesaleInvoice invoice = requireWithLines(id);
        if (invoice.getStatus() == WholesaleInvoiceStatus.VOIDED) {
            return WholesaleMapper.toInvoice(invoice);
        }
        if (invoice.isDraft()) {
            invoice.setStatus(WholesaleInvoiceStatus.VOIDED);
            invoice.setVoidedAt(Instant.now());
            invoice.setVoidReason(blankToNull(request == null ? null : request.reason()));
            return WholesaleMapper.toInvoice(invoiceRepository.save(invoice));
        }
        if (Money.isPositive(invoice.getAmountPaid())) {
            throw ApiException.of(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot void an invoice with collections; reverse payments first");
        }

        for (WholesaleInvoiceLine line : invoice.getLines()) {
            stockLedgerService.restore(
                    line.getItemId(),
                    line.getQuantity(),
                    DOC_TYPE,
                    invoice.getId(),
                    invoice.getInvoiceNumber());
        }
        stockLedgerService.returnSerials(SoldDocumentType.WHOLESALE_INVOICE, invoice.getId());

        if (Money.isPositive(invoice.getOutstandingAmount())) {
            Customer customer = customerRepository.findByIdAndDeletedAtIsNull(invoice.getCustomerId())
                    .orElseThrow(() -> ApiException.notFound("Customer", invoice.getCustomerId()));
            customer.adjustOutstanding(Money.negate(invoice.getOutstandingAmount()));
            if (customer.getOutstandingAmount().signum() < 0) {
                customer.setOutstandingAmount(Money.ZERO);
            }
            customerRepository.save(customer);
            appendLedger(customer, CreditLedgerEntryType.ADJUSTMENT,
                    Money.negate(invoice.getOutstandingAmount()),
                    DOC_TYPE, invoice.getId(), invoice.getInvoiceNumber(), "Invoice voided");
        }

        invoice.setStatus(WholesaleInvoiceStatus.VOIDED);
        invoice.setVoidedAt(Instant.now());
        invoice.setVoidReason(blankToNull(request == null ? null : request.reason()));
        invoice.setOutstandingAmount(Money.ZERO);
        invoice.setUpdatedBy(TenantContext.userIdOrNull());
        return WholesaleMapper.toInvoice(invoiceRepository.save(invoice));
    }

    private void reverseChequePayment(WholesaleInvoice invoice, Payment payment) {
        BigDecimal amount = Money.of(payment.getAmount());
        invoice.setAmountPaid(Money.subtract(invoice.getAmountPaid(), amount));
        if (invoice.getAmountPaid().signum() < 0) {
            invoice.setAmountPaid(Money.ZERO);
        }
        invoice.setOutstandingAmount(Money.subtract(invoice.getGrandTotal(), invoice.getAmountPaid()));
        invoice.setStatus(resolveStatus(invoice));
        invoice.setSettledAt(null);
        invoiceRepository.save(invoice);

        Customer customer = customerRepository.findByIdAndDeletedAtIsNull(invoice.getCustomerId())
                .orElseThrow(() -> ApiException.notFound("Customer", invoice.getCustomerId()));
        customer.adjustOutstanding(amount);
        if (Money.isPositive(customer.getOutstandingAmount())
                && Money.gt(customer.getOutstandingAmount(), customer.getCreditLimit())) {
            // Bounce can push over limit; still record — collections must chase it.
            customerRepository.save(customer);
        } else {
            customerRepository.save(customer);
        }
        appendLedger(customer, CreditLedgerEntryType.CHEQUE_BOUNCE, amount,
                DOC_TYPE, invoice.getId(), invoice.getInvoiceNumber(),
                "Cheque bounced: " + payment.getChequeNumber());
    }

    private WholesaleInvoiceLine buildLine(InvoiceLineRequest request, short lineNumber) {
        Item item = itemRepository.findByIdAndDeletedAtIsNull(request.itemId())
                .orElseThrow(() -> ApiException.notFound("Item", request.itemId()));
        if (!item.isSellable()) {
            throw ApiException.of(ErrorCode.ITEM_NOT_SELLABLE, "Item is not sellable")
                    .with("itemId", item.getId());
        }

        WholesalePriceMode priceMode = request.priceMode() == null
                ? WholesalePriceMode.WHOLESALE : request.priceMode();
        BigDecimal unitPrice = request.unitPrice() != null
                ? Money.of(request.unitPrice())
                : Money.of(item.priceFor(priceMode == WholesalePriceMode.WHOLESALE));

        WholesaleInvoiceLine line = new WholesaleInvoiceLine();
        line.setItemId(item.getId());
        line.setLineNumber(lineNumber);
        line.setItemSku(item.getSku());
        line.setItemName(item.getName());
        line.setUnitCost(Money.of(item.getCostPrice()));
        line.setPriceMode(priceMode);
        line.setQuantity(Money.quantity(request.quantity()));
        line.setUnitPrice(unitPrice);
        line.setDiscountType(request.discountType() == null ? DiscountType.NONE : request.discountType());
        line.setDiscountInput(Money.of(request.discountInput()));
        line.setTaxRateId(request.taxRateId() != null ? request.taxRateId() : item.getTaxRateId());
        line.setWarrantyLabel(request.warrantyLabel() != null
                ? request.warrantyLabel() : item.getWarrantyLabel());

        TaxRate taxRate = resolveTax(line.getTaxRateId());
        BigDecimal taxPercent = taxRate != null ? taxRate.getRatePercent() : Money.ZERO;
        boolean inclusive = taxRate != null && taxRate.isInclusive();
        line.setTaxRatePercent(taxPercent);

        SalesPricing.LineTotals totals = SalesPricing.computeLine(
                line.getQuantity(), line.getUnitPrice(), line.getDiscountType(),
                line.getDiscountInput(), taxPercent, inclusive);
        line.setGrossAmount(totals.grossAmount());
        line.setDiscountAmount(totals.discountAmount());
        line.setNetAmount(totals.netAmount());
        line.setTaxAmount(totals.taxAmount());
        line.setLineTotal(totals.lineTotal());

        if (item.isHasSerialTracking()) {
            List<UUID> serialIds = request.serialIds() == null ? List.of() : request.serialIds();
            int expected = line.getQuantity().intValueExact();
            if (serialIds.size() != expected) {
                throw ApiException.of(ErrorCode.SERIAL_COUNT_MISMATCH,
                                "Serialised items require one serial per unit")
                        .with("itemId", item.getId())
                        .with("expected", expected)
                        .with("actual", serialIds.size());
            }
            if (!serialIds.isEmpty()) {
                List<ItemSerial> serials = itemSerialRepository.findByIdIn(serialIds);
                Map<UUID, ItemSerial> byId = new HashMap<>();
                for (ItemSerial s : serials) {
                    byId.put(s.getId(), s);
                }
                for (UUID serialId : serialIds) {
                    ItemSerial serial = byId.get(serialId);
                    if (serial == null || !serial.getItemId().equals(item.getId()) || !serial.isAvailable()) {
                        throw ApiException.of(ErrorCode.SERIAL_NOT_AVAILABLE,
                                "Serial not available for this item");
                    }
                    WholesaleInvoiceLineSerial wls = new WholesaleInvoiceLineSerial();
                    wls.setItemSerialId(serial.getId());
                    wls.setSerialNumber(serial.getSerialNumber());
                    line.addSerial(wls);
                }
            }
        }
        return line;
    }

    private void recalculate(WholesaleInvoice invoice) {
        BigDecimal subtotal = Money.ZERO;
        BigDecimal lineDiscount = Money.ZERO;
        BigDecimal taxTotal = Money.ZERO;
        BigDecimal linesGrand = Money.ZERO;
        BigDecimal cogs = Money.ZERO;

        for (WholesaleInvoiceLine line : invoice.getLines()) {
            subtotal = Money.add(subtotal, line.getGrossAmount());
            lineDiscount = Money.add(lineDiscount, line.getDiscountAmount());
            taxTotal = Money.add(taxTotal, line.getTaxAmount());
            linesGrand = Money.add(linesGrand, line.getLineTotal());
            cogs = Money.add(cogs, Money.of(line.getQuantity().multiply(line.getUnitCost())));
        }

        BigDecimal merchandiseNet = Money.subtract(subtotal, lineDiscount);
        BigDecimal invoiceDiscount = SalesPricing.discountAmount(
                invoice.getInvoiceDiscountType(),
                invoice.getInvoiceDiscountInput(),
                merchandiseNet);
        BigDecimal grand = Money.subtract(linesGrand, invoiceDiscount);

        invoice.setSubtotal(subtotal);
        invoice.setLineDiscountTotal(lineDiscount);
        invoice.setInvoiceDiscountAmount(invoiceDiscount);
        invoice.setTaxTotal(taxTotal);
        invoice.setGrandTotal(grand);
        invoice.setCostOfGoods(cogs);
        invoice.setOutstandingAmount(Money.subtract(grand, Money.of(invoice.getAmountPaid())));
    }

    private Payment createPayment(WholesaleInvoice invoice, CollectionRequest request, BigDecimal applied) {
        if (request.method() == PaymentMethod.CHEQUE
                && (request.chequeNumber() == null || request.chequeDate() == null)) {
            throw ApiException.validation("Cheque payments require chequeNumber and chequeDate");
        }
        Payment payment = new Payment();
        payment.setOutletId(invoice.getOutletId());
        payment.setDocumentType(PaymentDocumentType.WHOLESALE_INVOICE);
        payment.setDocumentId(invoice.getId());
        payment.setCustomerId(invoice.getCustomerId());
        payment.setMethod(request.method());
        payment.setDirection(PaymentDirection.IN);
        payment.setAmount(applied);
        payment.setCurrency(invoice.getCurrency());
        payment.setTenderedAmount(request.tenderedAmount() == null ? null : Money.of(request.tenderedAmount()));
        payment.setChangeAmount(request.method() == PaymentMethod.CASH && payment.getTenderedAmount() != null
                ? Money.max(Money.subtract(payment.getTenderedAmount(), applied), Money.ZERO)
                : Money.ZERO);
        payment.setReference(request.reference());
        payment.setCardLast4(request.cardLast4());
        payment.setBankName(request.bankName());
        payment.setChequeNumber(request.chequeNumber());
        payment.setChequeDate(request.chequeDate());
        if (request.method() == PaymentMethod.CHEQUE) {
            payment.setChequeStatus("PENDING");
        }
        payment.setNote(request.note());
        payment.setReceivedAt(Instant.now());
        payment.setCreatedBy(TenantContext.userIdOrNull());
        return payment;
    }

    private void appendLedger(Customer customer,
                              CreditLedgerEntryType type,
                              BigDecimal delta,
                              String documentType,
                              UUID documentId,
                              String documentNumber,
                              String note) {
        if (delta == null || delta.signum() == 0) {
            return;
        }
        CustomerCreditLedger entry = new CustomerCreditLedger();
        entry.setCustomerId(customer.getId());
        entry.setEntryType(type);
        entry.setAmountDelta(Money.of(delta));
        entry.setBalanceAfter(Money.of(customer.getOutstandingAmount()));
        entry.setDocumentType(documentType);
        entry.setDocumentId(documentId);
        entry.setDocumentNumber(documentNumber);
        entry.setNote(note);
        entry.setOccurredAt(Instant.now());
        entry.setCreatedBy(TenantContext.userIdOrNull());
        ledgerRepository.save(entry);
    }

    private WholesaleInvoiceStatus resolveStatus(WholesaleInvoice invoice) {
        if (!Money.isPositive(invoice.getOutstandingAmount())) {
            return WholesaleInvoiceStatus.SETTLED;
        }
        if (Money.isPositive(invoice.getAmountPaid())) {
            return WholesaleInvoiceStatus.PARTIALLY_PAID;
        }
        if (invoice.getDueDate() != null && invoice.getDueDate().isBefore(LocalDate.now())) {
            return WholesaleInvoiceStatus.OVERDUE;
        }
        return WholesaleInvoiceStatus.POSTED;
    }

    private void requireDraft(WholesaleInvoice invoice) {
        if (!invoice.isDraft()) {
            throw ApiException.of(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Invoice is not editable in status " + invoice.getStatus());
        }
    }

    private WholesaleInvoice requireWithLines(UUID id) {
        return invoiceRepository.findByIdWithLines(id)
                .orElseThrow(() -> ApiException.notFound("WholesaleInvoice", id));
    }

    private TaxRate resolveTax(UUID taxRateId) {
        if (taxRateId == null) {
            return null;
        }
        return taxRateRepository.findById(taxRateId).orElse(null);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
