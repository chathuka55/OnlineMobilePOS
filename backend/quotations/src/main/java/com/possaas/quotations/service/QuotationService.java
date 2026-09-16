package com.possaas.quotations.service;

import com.possaas.catalog.domain.Item;
import com.possaas.catalog.repository.ItemRepository;
import com.possaas.common.api.PageResponse;
import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.money.Money;
import com.possaas.common.tenant.TenantContext;
import com.possaas.crm.domain.Customer;
import com.possaas.crm.repository.CustomerRepository;
import com.possaas.quotations.domain.Quotation;
import com.possaas.quotations.domain.QuotationLine;
import com.possaas.quotations.domain.QuotationStatus;
import com.possaas.quotations.dto.QuotationDtos.ConvertResponse;
import com.possaas.quotations.dto.QuotationDtos.CreateQuotationRequest;
import com.possaas.quotations.dto.QuotationDtos.QuotationLineRequest;
import com.possaas.quotations.dto.QuotationDtos.QuotationPrintResponse;
import com.possaas.quotations.dto.QuotationDtos.QuotationResponse;
import com.possaas.quotations.dto.QuotationDtos.TransitionRequest;
import com.possaas.quotations.dto.QuotationDtos.UpdateQuotationRequest;
import com.possaas.quotations.repository.QuotationRepository;
import com.possaas.sales.domain.DiscountType;
import com.possaas.sales.domain.PriceMode;
import com.possaas.sales.dto.SalesDtos.CartLineRequest;
import com.possaas.sales.dto.SalesDtos.CartResponse;
import com.possaas.sales.dto.SalesDtos.CreateCartRequest;
import com.possaas.sales.service.CartService;
import com.possaas.sales.service.SalesPricing;
import com.possaas.tenancy.domain.DocumentType;
import com.possaas.tenancy.domain.TaxRate;
import com.possaas.tenancy.repository.TaxRateRepository;
import com.possaas.tenancy.service.DocumentNumberService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuotationService {

    private static final Map<QuotationStatus, Set<QuotationStatus>> TRANSITIONS =
            new EnumMap<>(QuotationStatus.class);

    static {
        TRANSITIONS.put(QuotationStatus.DRAFT, EnumSet.of(
                QuotationStatus.SENT, QuotationStatus.ACCEPTED, QuotationStatus.CANCELLED,
                QuotationStatus.EXPIRED));
        TRANSITIONS.put(QuotationStatus.SENT, EnumSet.of(
                QuotationStatus.ACCEPTED, QuotationStatus.REJECTED, QuotationStatus.EXPIRED,
                QuotationStatus.CANCELLED, QuotationStatus.DRAFT));
        TRANSITIONS.put(QuotationStatus.ACCEPTED, EnumSet.of(
                QuotationStatus.CONVERTED, QuotationStatus.CANCELLED, QuotationStatus.EXPIRED));
        TRANSITIONS.put(QuotationStatus.REJECTED, EnumSet.noneOf(QuotationStatus.class));
        TRANSITIONS.put(QuotationStatus.EXPIRED, EnumSet.noneOf(QuotationStatus.class));
        TRANSITIONS.put(QuotationStatus.CONVERTED, EnumSet.noneOf(QuotationStatus.class));
        TRANSITIONS.put(QuotationStatus.CANCELLED, EnumSet.noneOf(QuotationStatus.class));
    }

    private final QuotationRepository quotationRepository;
    private final ItemRepository itemRepository;
    private final CustomerRepository customerRepository;
    private final TaxRateRepository taxRateRepository;
    private final DocumentNumberService documentNumberService;
    private final CartService cartService;

    public QuotationService(QuotationRepository quotationRepository,
                            ItemRepository itemRepository,
                            CustomerRepository customerRepository,
                            TaxRateRepository taxRateRepository,
                            DocumentNumberService documentNumberService,
                            CartService cartService) {
        this.quotationRepository = quotationRepository;
        this.itemRepository = itemRepository;
        this.customerRepository = customerRepository;
        this.taxRateRepository = taxRateRepository;
        this.documentNumberService = documentNumberService;
        this.cartService = cartService;
    }

    @Transactional(readOnly = true)
    public PageResponse<QuotationResponse> list(String q, QuotationStatus status, UUID customerId,
                                                Pageable pageable) {
        return PageResponse.of(
                quotationRepository.search(blankToNull(q), status, customerId, pageable)
                        .map(QuotationMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public QuotationResponse get(UUID id) {
        return QuotationMapper.toResponse(requireWithLines(id));
    }

    @Transactional(readOnly = true)
    public QuotationPrintResponse print(UUID id) {
        Quotation quotation = requireWithLines(id);
        expireIfNeeded(quotation);
        return new QuotationPrintResponse(QuotationMapper.toResponse(quotation));
    }

    @Transactional
    public QuotationResponse create(CreateQuotationRequest request) {
        Quotation quotation = new Quotation();
        quotation.setOutletId(TenantContext.requireOutletId());
        quotation.setQuotationNumber(documentNumberService.next(DocumentType.QUOTATION));
        quotation.setStatus(QuotationStatus.DRAFT);
        quotation.setQuotedAt(Instant.now());
        quotation.setCreatedBy(TenantContext.userIdOrNull());
        quotation.setPriceMode(request.priceMode() == null ? PriceMode.RETAIL : request.priceMode());
        quotation.setValidUntil(request.validUntil());
        quotation.setQuoteDiscountType(request.quoteDiscountType() == null
                ? DiscountType.NONE : request.quoteDiscountType());
        quotation.setQuoteDiscountInput(Money.of(request.quoteDiscountInput()));
        quotation.setTerms(blankToNull(request.terms()));
        quotation.setNote(blankToNull(request.note()));
        applyCustomer(quotation, request.customerId(), request.customerName(),
                request.customerPhone(), request.customerEmail());

        if (request.lines() != null) {
            short n = 1;
            for (QuotationLineRequest lineRequest : request.lines()) {
                quotation.addLine(buildLine(lineRequest, n++));
            }
        }
        recalculate(quotation);
        return QuotationMapper.toResponse(quotationRepository.save(quotation));
    }

    @Transactional
    public QuotationResponse update(UUID id, UpdateQuotationRequest request) {
        Quotation quotation = requireWithLines(id);
        expireIfNeeded(quotation);
        if (!quotation.isEditable()) {
            throw ApiException.of(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Quotation cannot be updated in status " + quotation.getStatus());
        }
        if (request.customerId() != null || request.customerName() != null
                || request.customerPhone() != null || request.customerEmail() != null) {
            applyCustomer(quotation,
                    request.customerId() != null ? request.customerId() : quotation.getCustomerId(),
                    request.customerName() != null ? request.customerName() : quotation.getCustomerName(),
                    request.customerPhone() != null ? request.customerPhone() : quotation.getCustomerPhone(),
                    request.customerEmail() != null ? request.customerEmail() : quotation.getCustomerEmail());
        }
        if (request.priceMode() != null) {
            quotation.setPriceMode(request.priceMode());
        }
        if (request.validUntil() != null) {
            quotation.setValidUntil(request.validUntil());
        }
        if (request.quoteDiscountType() != null) {
            quotation.setQuoteDiscountType(request.quoteDiscountType());
        }
        if (request.quoteDiscountInput() != null) {
            quotation.setQuoteDiscountInput(Money.of(request.quoteDiscountInput()));
        }
        if (request.terms() != null) {
            quotation.setTerms(blankToNull(request.terms()));
        }
        if (request.note() != null) {
            quotation.setNote(blankToNull(request.note()));
        }
        quotation.setUpdatedBy(TenantContext.userIdOrNull());
        recalculate(quotation);
        return QuotationMapper.toResponse(quotationRepository.save(quotation));
    }

    @Transactional
    public QuotationResponse addLine(UUID id, QuotationLineRequest request) {
        Quotation quotation = requireWithLines(id);
        expireIfNeeded(quotation);
        if (!quotation.isEditable()) {
            throw ApiException.of(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot add lines in status " + quotation.getStatus());
        }
        short next = (short) (quotation.getLines().stream()
                .mapToInt(QuotationLine::getLineNumber).max().orElse(0) + 1);
        quotation.addLine(buildLine(request, next));
        quotation.setUpdatedBy(TenantContext.userIdOrNull());
        recalculate(quotation);
        return QuotationMapper.toResponse(quotationRepository.save(quotation));
    }

    @Transactional
    public QuotationResponse removeLine(UUID id, UUID lineId) {
        Quotation quotation = requireWithLines(id);
        if (!quotation.isEditable()) {
            throw ApiException.of(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot remove lines in status " + quotation.getStatus());
        }
        QuotationLine line = quotation.getLines().stream()
                .filter(l -> l.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("QuotationLine", lineId));
        quotation.getLines().remove(line);
        short n = 1;
        for (QuotationLine remaining : quotation.getLines()) {
            remaining.setLineNumber(n++);
        }
        recalculate(quotation);
        return QuotationMapper.toResponse(quotationRepository.save(quotation));
    }

    @Transactional
    public QuotationResponse transition(UUID id, TransitionRequest request) {
        Quotation quotation = requireWithLines(id);
        expireIfNeeded(quotation);
        QuotationStatus from = quotation.getStatus();
        QuotationStatus to = request.status();
        if (from == to) {
            return QuotationMapper.toResponse(quotation);
        }
        if (to == QuotationStatus.CONVERTED) {
            throw ApiException.of(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Use convert endpoint to convert a quotation");
        }
        Set<QuotationStatus> allowed = TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw ApiException.of(ErrorCode.INVALID_STATUS_TRANSITION,
                            "Cannot transition quotation from " + from + " to " + to)
                    .with("from", from.name())
                    .with("to", to.name());
        }
        applyStatusTimestamps(quotation, to);
        quotation.setStatus(to);
        quotation.setUpdatedBy(TenantContext.userIdOrNull());
        return QuotationMapper.toResponse(quotationRepository.save(quotation));
    }

    /**
     * Prefills a draft cart from quotation lines. Does not reserve stock.
     * Marks the quote ACCEPTED when still DRAFT/SENT; CONVERTED requires a bill id
     * so that status is applied only when a bill is later linked.
     */
    @Transactional
    public ConvertResponse convertToCart(UUID id) {
        Quotation quotation = requireWithLines(id);
        expireIfNeeded(quotation);
        if (quotation.getStatus() == QuotationStatus.CONVERTED
                || quotation.getStatus() == QuotationStatus.CANCELLED
                || quotation.getStatus() == QuotationStatus.REJECTED
                || quotation.getStatus() == QuotationStatus.EXPIRED) {
            throw ApiException.of(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Quotation cannot be converted in status " + quotation.getStatus());
        }
        if (quotation.getLines().isEmpty()) {
            throw ApiException.of(ErrorCode.CART_EMPTY, "Quotation has no lines to convert");
        }

        for (QuotationLine line : quotation.getLines()) {
            if (line.getItemId() == null) {
                throw ApiException.validation(
                                "All quotation lines must reference a catalog item to convert to a cart")
                        .with("lineNumber", line.getLineNumber());
            }
        }

        CartResponse cart = cartService.create(new CreateCartRequest(
                quotation.getCustomerId(),
                quotation.getCustomerName(),
                quotation.getPriceMode(),
                "From quote " + quotation.getQuotationNumber(),
                quotation.getNote()));

        for (QuotationLine line : quotation.getLines()) {
            cart = cartService.addLine(cart.id(), new CartLineRequest(
                    line.getItemId(),
                    line.getQuantity(),
                    line.getUnitPrice(),
                    line.getDiscountType(),
                    line.getDiscountInput(),
                    line.getTaxRateId(),
                    line.getWarrantyLabel(),
                    line.getDescription() != null ? line.getDescription() : line.getItemName(),
                    null));
        }

        if (quotation.getStatus() == QuotationStatus.DRAFT
                || quotation.getStatus() == QuotationStatus.SENT) {
            quotation.setStatus(QuotationStatus.ACCEPTED);
            quotation.setAcceptedAt(Instant.now());
        }
        quotation.setUpdatedBy(TenantContext.userIdOrNull());
        quotation = quotationRepository.save(quotation);

        return new ConvertResponse(QuotationMapper.toResponse(quotation), cart);
    }

    private QuotationLine buildLine(QuotationLineRequest request, short lineNumber) {
        QuotationLine line = new QuotationLine();
        line.setLineNumber(lineNumber);
        line.setItemName(request.itemName().trim());
        line.setDescription(request.description());
        line.setItemSku(blankToNull(request.itemSku()));
        line.setQuantity(Money.quantity(request.quantity()));
        line.setUnitPrice(Money.of(request.unitPrice()));
        line.setDiscountType(request.discountType() == null ? DiscountType.NONE : request.discountType());
        line.setDiscountInput(Money.of(request.discountInput()));
        line.setWarrantyLabel(request.warrantyLabel());
        line.setTaxRateId(request.taxRateId());

        if (request.itemId() != null) {
            Item item = itemRepository.findByIdAndDeletedAtIsNull(request.itemId())
                    .orElseThrow(() -> ApiException.notFound("Item", request.itemId()));
            line.setItemId(item.getId());
            if (line.getItemSku() == null) {
                line.setItemSku(item.getSku());
            }
            if (line.getTaxRateId() == null) {
                line.setTaxRateId(item.getTaxRateId());
            }
            if (line.getWarrantyLabel() == null) {
                line.setWarrantyLabel(item.getWarrantyLabel());
            }
        }

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
        return line;
    }

    private void recalculate(Quotation quotation) {
        BigDecimal subtotal = Money.ZERO;
        BigDecimal lineDiscount = Money.ZERO;
        BigDecimal taxTotal = Money.ZERO;
        BigDecimal linesGrand = Money.ZERO;

        for (QuotationLine line : quotation.getLines()) {
            subtotal = Money.add(subtotal, line.getGrossAmount());
            lineDiscount = Money.add(lineDiscount, line.getDiscountAmount());
            taxTotal = Money.add(taxTotal, line.getTaxAmount());
            linesGrand = Money.add(linesGrand, line.getLineTotal());
        }

        BigDecimal merchandiseNet = Money.subtract(subtotal, lineDiscount);
        BigDecimal quoteDiscount = SalesPricing.discountAmount(
                quotation.getQuoteDiscountType(),
                quotation.getQuoteDiscountInput(),
                merchandiseNet);

        quotation.setSubtotal(subtotal);
        quotation.setLineDiscountTotal(lineDiscount);
        quotation.setQuoteDiscountAmount(quoteDiscount);
        quotation.setTaxTotal(taxTotal);
        quotation.setGrandTotal(Money.subtract(linesGrand, quoteDiscount));
    }

    private void applyCustomer(Quotation quotation, UUID customerId, String name,
                               String phone, String email) {
        if (customerId != null) {
            Customer customer = customerRepository.findByIdAndDeletedAtIsNull(customerId)
                    .orElseThrow(() -> ApiException.notFound("Customer", customerId));
            quotation.setCustomerId(customer.getId());
            quotation.setCustomerName(name != null && !name.isBlank() ? name.trim() : customer.getDisplayName());
            quotation.setCustomerPhone(phone != null ? phone : customer.getPhonePrimary());
            quotation.setCustomerEmail(email != null ? blankToNull(email) : customer.getEmail());
        } else {
            quotation.setCustomerId(null);
            quotation.setCustomerName(name == null || name.isBlank() ? "Walk-in Customer" : name.trim());
            quotation.setCustomerPhone(phone);
            quotation.setCustomerEmail(blankToNull(email));
        }
    }

    private void applyStatusTimestamps(Quotation quotation, QuotationStatus to) {
        Instant now = Instant.now();
        switch (to) {
            case SENT -> quotation.setSentAt(now);
            case ACCEPTED -> quotation.setAcceptedAt(now);
            case REJECTED -> quotation.setRejectedAt(now);
            default -> {
            }
        }
    }

    private void expireIfNeeded(Quotation quotation) {
        if (quotation.getValidUntil() != null
                && quotation.getValidUntil().isBefore(LocalDate.now())
                && (quotation.getStatus() == QuotationStatus.DRAFT
                || quotation.getStatus() == QuotationStatus.SENT)) {
            quotation.setStatus(QuotationStatus.EXPIRED);
            quotationRepository.save(quotation);
        }
    }

    private Quotation requireWithLines(UUID id) {
        return quotationRepository.findByIdWithLines(id)
                .orElseThrow(() -> ApiException.notFound("Quotation", id));
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
