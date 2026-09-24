package com.possaas.sales.service;

import com.possaas.catalog.domain.Item;
import com.possaas.catalog.domain.ItemSerial;
import com.possaas.catalog.domain.SoldDocumentType;
import com.possaas.catalog.repository.ItemRepository;
import com.possaas.catalog.repository.ItemSerialRepository;
import com.possaas.catalog.service.StockLedgerService;
import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.money.Money;
import com.possaas.common.tenant.TenantContext;
import com.possaas.crm.domain.Customer;
import com.possaas.crm.repository.CustomerRepository;
import com.possaas.sales.domain.Bill;
import com.possaas.sales.domain.BillChannel;
import com.possaas.sales.domain.BillLine;
import com.possaas.sales.domain.BillLineSerial;
import com.possaas.sales.domain.BillStatus;
import com.possaas.sales.domain.Cart;
import com.possaas.sales.domain.CartLine;
import com.possaas.sales.domain.CartStatus;
import com.possaas.sales.domain.CreditNote;
import com.possaas.sales.domain.CreditNoteDocumentType;
import com.possaas.sales.domain.CreditNoteRedemption;
import com.possaas.sales.domain.CreditNoteStatus;
import com.possaas.sales.domain.DiscountType;
import com.possaas.sales.domain.Payment;
import com.possaas.sales.domain.PaymentDirection;
import com.possaas.sales.domain.PaymentDocumentType;
import com.possaas.sales.domain.PaymentMethod;
import com.possaas.sales.domain.PriceMode;
import com.possaas.sales.dto.SalesDtos.BillResponse;
import com.possaas.sales.dto.SalesDtos.CheckoutLineRequest;
import com.possaas.sales.dto.SalesDtos.CheckoutRequest;
import com.possaas.sales.dto.SalesDtos.CreditNoteApplicationRequest;
import com.possaas.sales.dto.SalesDtos.PaymentRequest;
import com.possaas.sales.repository.BillRepository;
import com.possaas.sales.repository.CartRepository;
import com.possaas.sales.repository.CreditNoteRedemptionRepository;
import com.possaas.sales.repository.CreditNoteRepository;
import com.possaas.sales.repository.PaymentRepository;
import com.possaas.tenancy.domain.DocumentType;
import com.possaas.tenancy.domain.TaxRate;
import com.possaas.tenancy.repository.TaxRateRepository;
import com.possaas.tenancy.service.TenantService;
import com.possaas.tenancy.service.DocumentNumberService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutService {

    private final BillRepository billRepository;
    private final CartRepository cartRepository;
    private final PaymentRepository paymentRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final CreditNoteRedemptionRepository redemptionRepository;
    private final ItemRepository itemRepository;
    private final ItemSerialRepository itemSerialRepository;
    private final CustomerRepository customerRepository;
    private final TaxRateRepository taxRateRepository;
    private final TenantService tenantService;
    private final DocumentNumberService documentNumberService;
    private final StockLedgerService stockLedgerService;

    public CheckoutService(BillRepository billRepository,
                           CartRepository cartRepository,
                           PaymentRepository paymentRepository,
                           CreditNoteRepository creditNoteRepository,
                           CreditNoteRedemptionRepository redemptionRepository,
                           ItemRepository itemRepository,
                           ItemSerialRepository itemSerialRepository,
                           CustomerRepository customerRepository,
                           TaxRateRepository taxRateRepository,
                           TenantService tenantService,
                           DocumentNumberService documentNumberService,
                           StockLedgerService stockLedgerService) {
        this.billRepository = billRepository;
        this.cartRepository = cartRepository;
        this.paymentRepository = paymentRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.redemptionRepository = redemptionRepository;
        this.itemRepository = itemRepository;
        this.itemSerialRepository = itemSerialRepository;
        this.customerRepository = customerRepository;
        this.taxRateRepository = taxRateRepository;
        this.tenantService = tenantService;
        this.documentNumberService = documentNumberService;
        this.stockLedgerService = stockLedgerService;
    }

    @Transactional
    public BillResponse checkout(CheckoutRequest request) {
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            var existing = billRepository.findByIdempotencyKey(request.idempotencyKey().trim());
            if (existing.isPresent()) {
                Bill bill = billRepository.findByIdWithLines(existing.get().getId()).orElse(existing.get());
                return SalesMapper.toBill(bill, paymentsFor(bill.getId()));
            }
        }

        Cart cart = null;
        List<LineInput> lineInputs;
        if (request.cartId() != null) {
            cart = cartRepository.findByIdWithLines(request.cartId())
                    .orElseThrow(() -> ApiException.notFound("Cart", request.cartId()));
            if (cart.getStatus() == CartStatus.CONVERTED) {
                throw ApiException.of(ErrorCode.CART_ALREADY_CONVERTED, "Cart already converted");
            }
            if (!cart.isEditable()) {
                throw ApiException.of(ErrorCode.INVALID_STATUS_TRANSITION,
                        "Cart cannot be checked out in status " + cart.getStatus());
            }
            if (cart.getLines().isEmpty()) {
                throw ApiException.of(ErrorCode.CART_EMPTY, "Cart has no lines");
            }
            lineInputs = cart.getLines().stream().map(LineInput::fromCart).toList();
        } else if (request.lines() != null && !request.lines().isEmpty()) {
            lineInputs = request.lines().stream().map(LineInput::fromCheckout).toList();
        } else {
            throw ApiException.of(ErrorCode.CART_EMPTY, "Checkout requires a cart or line items");
        }

        UUID outletId = cart != null ? cart.getOutletId() : TenantContext.requireOutletId();
        PriceMode priceMode = request.priceMode() != null
                ? request.priceMode()
                : (cart != null ? cart.getPriceMode() : PriceMode.RETAIL);

        Bill bill = new Bill();
        bill.setOutletId(outletId);
        bill.setBillNumber(documentNumberService.next(DocumentType.BILL));
        bill.setChannel(request.channel() == null ? BillChannel.RETAIL : request.channel());
        bill.setPriceMode(priceMode);
        bill.setNote(request.note() != null ? request.note() : (cart != null ? cart.getNote() : null));
        bill.setDeviceId(request.deviceId());
        bill.setDueDate(request.dueDate());
        bill.setBilledAt(Instant.now());
        bill.setCreatedBy(TenantContext.userIdOrNull());
        bill.setSourceCartId(cart != null ? cart.getId() : null);
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            bill.setIdempotencyKey(request.idempotencyKey().trim());
        }
        resolveCustomer(bill, request, cart);

        BigDecimal subtotal = Money.ZERO;
        BigDecimal lineDiscountTotal = Money.ZERO;
        BigDecimal taxTotal = Money.ZERO;
        BigDecimal linesGrand = Money.ZERO;
        BigDecimal costOfGoods = Money.ZERO;
        short lineNo = 1;
        Map<UUID, TaxRate> taxCache = new HashMap<>();
        boolean vatRegistered = tenantService.currentTenant().isVatRegistered();

        for (LineInput input : lineInputs) {
            Item item = itemRepository.findById(input.itemId())
                    .orElseThrow(() -> ApiException.notFound("Item", input.itemId()));
            if (!item.isSellable()) {
                throw ApiException.of(ErrorCode.ITEM_NOT_SELLABLE, "Item is not sellable")
                        .with("itemId", item.getId());
            }

            TaxRate taxRate = resolveTax(input.taxRateId() != null ? input.taxRateId() : item.getTaxRateId(), taxCache);
            // A shop that isn't VAT-registered may not charge VAT, whatever rate the
            // item carries - the rate can exist in the catalogue ahead of registration.
            BigDecimal taxPercent = (taxRate != null && vatRegistered)
                    ? taxRate.getRatePercent() : Money.ZERO;
            boolean inclusive = taxRate != null && taxRate.isInclusive();

            SalesPricing.LineTotals totals = SalesPricing.computeLine(
                    input.quantity(),
                    input.unitPrice(),
                    input.discountType(),
                    input.discountInput(),
                    taxPercent,
                    inclusive);

            BillLine line = new BillLine();
            line.setItemId(item.getId());
            line.setLineNumber(lineNo++);
            line.setItemSku(item.getSku());
            line.setItemName(item.getName());
            line.setUnitCost(Money.of(item.getCostPrice()));
            line.setQuantity(Money.quantity(input.quantity()));
            line.setUnitPrice(Money.of(input.unitPrice()));
            line.setGrossAmount(totals.grossAmount());
            line.setDiscountType(input.discountType() == null ? DiscountType.NONE : input.discountType());
            line.setDiscountInput(Money.of(input.discountInput()));
            line.setDiscountAmount(totals.discountAmount());
            line.setNetAmount(totals.netAmount());
            line.setTaxRateId(taxRate != null ? taxRate.getId() : null);
            line.setTaxRatePercent(taxPercent);
            line.setTaxAmount(totals.taxAmount());
            line.setTaxInclusive(inclusive);
            line.setLineTotal(totals.lineTotal());
            line.setWarrantyLabel(input.warrantyLabel() != null ? input.warrantyLabel() : item.getWarrantyLabel());
            line.setWarrantyMonths(item.getWarrantyMonths());
            if (item.getWarrantyMonths() > 0) {
                line.setWarrantyEndsOn(LocalDate.now().plusMonths(item.getWarrantyMonths()));
            }

            UUID[] serialIds = input.serialIds();
            if (item.isHasSerialTracking()) {
                int expected = line.getQuantity().intValueExact();
                int actual = serialIds == null ? 0 : serialIds.length;
                if (expected != actual) {
                    throw ApiException.of(ErrorCode.SERIAL_COUNT_MISMATCH,
                                    "Serialised items require one serial per unit")
                            .with("itemId", item.getId())
                            .with("expected", expected)
                            .with("actual", actual);
                }
                if (serialIds != null && serialIds.length > 0) {
                    List<ItemSerial> serials = itemSerialRepository.findByIdIn(List.of(serialIds));
                    Map<UUID, ItemSerial> byId = new HashMap<>();
                    for (ItemSerial s : serials) {
                        byId.put(s.getId(), s);
                    }
                    for (UUID serialId : serialIds) {
                        ItemSerial serial = byId.get(serialId);
                        if (serial == null || !serial.getItemId().equals(item.getId())) {
                            throw ApiException.of(ErrorCode.SERIAL_NOT_AVAILABLE,
                                    "Serial not available for this item");
                        }
                        BillLineSerial bls = new BillLineSerial();
                        bls.setItemSerialId(serial.getId());
                        bls.setSerialNumber(serial.getSerialNumber());
                        line.addSerial(bls);
                    }
                }
            }

            bill.addLine(line);
            subtotal = Money.add(subtotal, totals.grossAmount());
            lineDiscountTotal = Money.add(lineDiscountTotal, totals.discountAmount());
            taxTotal = Money.add(taxTotal, totals.taxAmount());
            linesGrand = Money.add(linesGrand, totals.lineTotal());
            costOfGoods = Money.add(costOfGoods,
                    Money.of(line.getQuantity().multiply(line.getUnitCost())));
        }

        DiscountType billDiscountType = request.billDiscountType() == null
                ? DiscountType.NONE : request.billDiscountType();
        BigDecimal billDiscountInput = Money.of(request.billDiscountInput());
        BigDecimal merchandiseNet = Money.subtract(subtotal, lineDiscountTotal);
        BigDecimal billDiscountAmount = SalesPricing.discountAmount(
                billDiscountType, billDiscountInput, merchandiseNet);

        BigDecimal grandTotal = Money.subtract(linesGrand, billDiscountAmount);

        // A bill discount reduces what the customer pays, so it has to reduce the tax
        // they are charged too. Line tax above was computed before the discount
        // existed; restate it on each line's share of the discounted total, otherwise
        // the printed VAT describes an amount nobody paid.
        if (Money.isPositive(billDiscountAmount)) {
            taxTotal = applyBillDiscountToLines(bill.getLines(), billDiscountAmount);
        }

        bill.setSubtotal(subtotal);
        bill.setLineDiscountTotal(lineDiscountTotal);
        bill.setBillDiscountType(billDiscountType);
        bill.setBillDiscountInput(billDiscountInput);
        bill.setBillDiscountAmount(billDiscountAmount);
        bill.setTaxTotal(taxTotal);
        bill.setRoundingAdjustment(Money.ZERO);
        bill.setGrandTotal(grandTotal);
        bill.setCostOfGoods(costOfGoods);
        bill.setCreditApplied(Money.ZERO);
        bill.setAmountPaid(Money.ZERO);
        bill.setBalanceDue(grandTotal);

        bill = billRepository.save(bill);

        // Stock + serials only at checkout
        for (BillLine line : bill.getLines()) {
            stockLedgerService.sell(
                    line.getItemId(),
                    line.getQuantity(),
                    "BILL",
                    bill.getId(),
                    bill.getBillNumber());
            if (!line.getSerials().isEmpty()) {
                List<UUID> serialIds = line.getSerials().stream()
                        .map(BillLineSerial::getItemSerialId)
                        .toList();
                stockLedgerService.markSerialsSold(serialIds, SoldDocumentType.BILL, bill.getId());
            }
        }

        BigDecimal creditApplied = applyCreditNotes(
                bill,
                request.creditNoteApplications() == null ? List.of() : request.creditNoteApplications());
        bill.setCreditApplied(creditApplied);
        bill.setBalanceDue(Money.subtract(bill.getGrandTotal(), creditApplied));

        List<Payment> payments = createPayments(
                bill,
                request.payments() == null ? List.of() : request.payments());
        BigDecimal amountPaid = payments.stream()
                .map(Payment::getAmount)
                .reduce(Money.ZERO, Money::add);
        bill.setAmountPaid(amountPaid);
        // balance_due: positive = customer owes; negative = change due
        bill.setBalanceDue(Money.subtract(Money.subtract(bill.getGrandTotal(), bill.getCreditApplied()), amountPaid));
        bill.setStatus(resolveStatus(bill.getBalanceDue(), bill.getAmountPaid(), bill.getCreditApplied()));
        bill = billRepository.save(bill);

        if (cart != null) {
            cart.setStatus(CartStatus.CONVERTED);
            cart.setConvertedBillId(bill.getId());
            cartRepository.save(cart);
        }

        if (bill.getCustomerId() != null) {
            updateCustomerTotals(bill);
        }

        return SalesMapper.toBill(bill, payments);
    }

    private void resolveCustomer(Bill bill, CheckoutRequest request, Cart cart) {
        UUID customerId = request.customerId() != null
                ? request.customerId()
                : (cart != null ? cart.getCustomerId() : null);
        String name = request.customerName() != null
                ? request.customerName()
                : (cart != null ? cart.getCustomerName() : null);
        String phone = request.customerPhone();

        if (customerId != null) {
            Customer customer = customerRepository.findByIdAndDeletedAtIsNull(customerId)
                    .orElseThrow(() -> ApiException.notFound("Customer", customerId));
            bill.setCustomerId(customer.getId());
            bill.setCustomerName(name != null ? name : customer.getDisplayName());
            bill.setCustomerPhone(phone != null ? phone : customer.getPhonePrimary());
        } else {
            bill.setCustomerName(name != null && !name.isBlank() ? name : "Walk-in Customer");
            bill.setCustomerPhone(phone);
        }
    }

    /**
     * Spreads the bill discount over the lines and restates their tax, returning the
     * reconciled invoice tax. Line amounts are rewritten in place so the stored bill
     * and its printed copy agree line by line.
     */
    private BigDecimal applyBillDiscountToLines(List<BillLine> lines, BigDecimal billDiscount) {
        List<BillDiscountAllocation.LineInput> inputs = lines.stream()
                .map(l -> new BillDiscountAllocation.LineInput(
                        l.getNetAmount(), l.getTaxRatePercent(), l.isTaxInclusive()))
                .toList();

        List<BillDiscountAllocation.LineResult> allocated =
                BillDiscountAllocation.allocate(inputs, billDiscount);

        // Tax computed once on the discounted total is the figure the customer can
        // check against the bill, so the lines are reconciled to it rather than the
        // other way round.
        BigDecimal invoiceTax = Money.ZERO;
        BigDecimal inclusiveTotal = Money.ZERO;
        BigDecimal inclusiveRate = null;
        boolean uniformInclusive = true;
        for (int i = 0; i < lines.size(); i++) {
            BillLine line = lines.get(i);
            if (!line.isTaxInclusive()) {
                uniformInclusive = false;
                break;
            }
            if (inclusiveRate == null) {
                inclusiveRate = line.getTaxRatePercent();
            } else if (inclusiveRate.compareTo(line.getTaxRatePercent()) != 0) {
                uniformInclusive = false;
                break;
            }
            inclusiveTotal = Money.add(inclusiveTotal, allocated.get(i).lineTotal());
        }

        List<BigDecimal> taxes = allocated.stream()
                .map(BillDiscountAllocation.LineResult::taxAmount)
                .toList();
        if (uniformInclusive && inclusiveRate != null) {
            invoiceTax = Money.taxFromInclusive(inclusiveTotal, inclusiveRate);
            taxes = BillDiscountAllocation.reconcileTax(
                    taxes,
                    allocated.stream().map(BillDiscountAllocation.LineResult::lineTotal).toList(),
                    invoiceTax);
        } else {
            // Mixed rates or exclusive tax: the sum of the lines is the invoice figure.
            for (BigDecimal tax : taxes) {
                invoiceTax = Money.add(invoiceTax, tax);
            }
        }

        for (int i = 0; i < lines.size(); i++) {
            BillLine line = lines.get(i);
            BillDiscountAllocation.LineResult result = allocated.get(i);
            line.setAllocatedBillDiscount(result.allocatedDiscount());
            line.setTaxAmount(taxes.get(i));
            line.setLineTotal(result.lineTotal());
        }
        return invoiceTax;
    }

    private TaxRate resolveTax(UUID taxRateId, Map<UUID, TaxRate> cache) {
        if (taxRateId == null) {
            return null;
        }
        return cache.computeIfAbsent(taxRateId,
                id -> taxRateRepository.findById(id).orElse(null));
    }

    private BigDecimal applyCreditNotes(Bill bill, List<CreditNoteApplicationRequest> applications) {
        BigDecimal total = Money.ZERO;
        BigDecimal remaining = bill.getGrandTotal();
        for (CreditNoteApplicationRequest app : applications) {
            if (Money.isZero(remaining) || !Money.isPositive(remaining)) {
                break;
            }
            CreditNote note = creditNoteRepository.findByIdForUpdate(app.creditNoteId())
                    .orElseThrow(() -> ApiException.notFound("CreditNote", app.creditNoteId()));
            validateCreditNote(note, bill.getCustomerId());

            BigDecimal requested = Money.min(Money.of(app.amount()), remaining);
            if (Money.gt(requested, note.getBalanceAmount())) {
                throw ApiException.of(ErrorCode.CREDIT_NOTE_EXHAUSTED,
                                "Credit note has insufficient balance")
                        .with("creditNoteId", note.getId())
                        .with("available", note.getBalanceAmount())
                        .with("requested", requested);
            }

            note.setBalanceAmount(Money.subtract(note.getBalanceAmount(), requested));
            note.refreshStatus();
            creditNoteRepository.save(note);

            CreditNoteRedemption redemption = new CreditNoteRedemption();
            redemption.setCreditNoteId(note.getId());
            redemption.setDocumentType(CreditNoteDocumentType.BILL);
            redemption.setDocumentId(bill.getId());
            redemption.setDocumentNumber(bill.getBillNumber());
            redemption.setAmount(requested);
            redemption.setRedeemedAt(Instant.now());
            redemption.setCreatedBy(TenantContext.userIdOrNull());
            redemptionRepository.save(redemption);

            total = Money.add(total, requested);
            remaining = Money.subtract(remaining, requested);
        }
        return total;
    }

    private void validateCreditNote(CreditNote note, UUID billCustomerId) {
        if (note.getStatus() == CreditNoteStatus.VOIDED
                || note.getStatus() == CreditNoteStatus.FULLY_USED) {
            throw ApiException.of(ErrorCode.CREDIT_NOTE_EXHAUSTED, "Credit note is not usable")
                    .with("status", note.getStatus().name());
        }
        if (note.getExpiresOn() != null && note.getExpiresOn().isBefore(LocalDate.now())) {
            throw ApiException.of(ErrorCode.CREDIT_NOTE_EXPIRED, "Credit note has expired")
                    .with("creditNoteId", note.getId());
        }
        if (note.getCustomerId() != null && billCustomerId != null
                && !note.getCustomerId().equals(billCustomerId)) {
            throw ApiException.validation("Credit note belongs to a different customer");
        }
        if (!Money.isPositive(note.getBalanceAmount())) {
            throw ApiException.of(ErrorCode.CREDIT_NOTE_EXHAUSTED, "Credit note balance is zero");
        }
    }

    private List<Payment> createPayments(Bill bill, List<PaymentRequest> requests) {
        List<Payment> created = new ArrayList<>();
        BigDecimal remaining = Money.subtract(bill.getGrandTotal(), bill.getCreditApplied());
        for (PaymentRequest req : requests) {
            if (!Money.isPositive(remaining)) {
                break;
            }
            if (req.method() == PaymentMethod.CHEQUE
                    && (req.chequeNumber() == null || req.chequeDate() == null)) {
                throw ApiException.validation("Cheque payments require chequeNumber and chequeDate");
            }

            BigDecimal tendered = req.tenderedAmount() != null
                    ? Money.of(req.tenderedAmount()) : Money.of(req.amount());
            BigDecimal applied = Money.min(Money.of(req.amount()), remaining);
            if (req.method() == PaymentMethod.CASH && Money.gt(tendered, applied)) {
                applied = Money.min(tendered, remaining);
            }

            Payment payment = new Payment();
            payment.setOutletId(bill.getOutletId());
            payment.setDocumentType(PaymentDocumentType.BILL);
            payment.setDocumentId(bill.getId());
            payment.setCustomerId(bill.getCustomerId());
            payment.setMethod(req.method());
            payment.setDirection(PaymentDirection.IN);
            payment.setAmount(applied);
            payment.setCurrency(bill.getCurrency());
            payment.setTenderedAmount(req.tenderedAmount() != null ? Money.of(req.tenderedAmount()) : null);
            payment.setChangeAmount(req.method() == PaymentMethod.CASH && payment.getTenderedAmount() != null
                    ? Money.max(Money.subtract(payment.getTenderedAmount(), applied), Money.ZERO)
                    : Money.ZERO);
            payment.setReference(req.reference());
            payment.setCardLast4(req.cardLast4());
            payment.setBankName(req.bankName());
            payment.setChequeNumber(req.chequeNumber());
            payment.setChequeDate(req.chequeDate());
            if (req.method() == PaymentMethod.CHEQUE) {
                payment.setChequeStatus("PENDING");
            }
            payment.setNote(req.note());
            payment.setIdempotencyKey(req.idempotencyKey());
            payment.setReceivedAt(Instant.now());
            payment.setCreatedBy(TenantContext.userIdOrNull());
            created.add(paymentRepository.save(payment));
            remaining = Money.subtract(remaining, applied);
        }
        return created;
    }

    static BillStatus resolveStatus(BigDecimal balanceDue, BigDecimal amountPaid, BigDecimal creditApplied) {
        if (!Money.isPositive(balanceDue)) {
            return BillStatus.COMPLETED;
        }
        if (Money.isPositive(amountPaid) || Money.isPositive(creditApplied)) {
            return BillStatus.PARTIALLY_PAID;
        }
        return BillStatus.UNPAID;
    }

    private void updateCustomerTotals(Bill bill) {
        customerRepository.findById(bill.getCustomerId()).ifPresent(customer -> {
            customer.setLifetimeSales(Money.add(customer.getLifetimeSales(), bill.getGrandTotal()));
            if (Money.isPositive(bill.getBalanceDue())) {
                customer.setOutstandingAmount(Money.add(customer.getOutstandingAmount(), bill.getBalanceDue()));
            }
            customerRepository.save(customer);
        });
    }

    private List<Payment> paymentsFor(UUID billId) {
        return paymentRepository.findByDocumentTypeAndDocumentIdOrderByReceivedAtAsc(
                PaymentDocumentType.BILL, billId);
    }

    private record LineInput(
            UUID itemId,
            BigDecimal quantity,
            BigDecimal unitPrice,
            DiscountType discountType,
            BigDecimal discountInput,
            UUID taxRateId,
            String warrantyLabel,
            UUID[] serialIds
    ) {
        static LineInput fromCart(CartLine line) {
            return new LineInput(
                    line.getItemId(),
                    line.getQuantity(),
                    line.getUnitPrice(),
                    line.getDiscountType(),
                    line.getDiscountInput(),
                    line.getTaxRateId(),
                    line.getWarrantyLabel(),
                    line.getSerialIds()
            );
        }

        static LineInput fromCheckout(CheckoutLineRequest line) {
            UUID[] serials = line.serialIds() == null || line.serialIds().isEmpty()
                    ? new UUID[0]
                    : line.serialIds().toArray(UUID[]::new);
            return new LineInput(
                    line.itemId(),
                    line.quantity(),
                    line.unitPrice(),
                    line.discountType() == null ? DiscountType.NONE : line.discountType(),
                    line.discountInput() == null ? Money.ZERO : line.discountInput(),
                    line.taxRateId(),
                    line.warrantyLabel(),
                    serials
            );
        }
    }
}
