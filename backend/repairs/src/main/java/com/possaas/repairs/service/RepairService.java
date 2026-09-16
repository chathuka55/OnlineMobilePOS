package com.possaas.repairs.service;

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
import com.possaas.repairs.domain.RepairHistory;
import com.possaas.repairs.domain.RepairLine;
import com.possaas.repairs.domain.RepairLineSerial;
import com.possaas.repairs.domain.RepairLineType;
import com.possaas.repairs.domain.RepairOrder;
import com.possaas.repairs.domain.RepairOrderStatus;
import com.possaas.repairs.dto.RepairDtos.CreateRepairRequest;
import com.possaas.repairs.dto.RepairDtos.PaymentRequest;
import com.possaas.repairs.dto.RepairDtos.RefundRequest;
import com.possaas.repairs.dto.RepairDtos.RepairLineRequest;
import com.possaas.repairs.dto.RepairDtos.RepairResponse;
import com.possaas.repairs.dto.RepairDtos.TransitionRequest;
import com.possaas.repairs.dto.RepairDtos.UpdateRepairRequest;
import com.possaas.repairs.repository.RepairOrderRepository;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepairService {

    private static final Map<RepairOrderStatus, Set<RepairOrderStatus>> TRANSITIONS =
            new EnumMap<>(RepairOrderStatus.class);

    static {
        TRANSITIONS.put(RepairOrderStatus.RECEIVED, EnumSet.of(
                RepairOrderStatus.DIAGNOSING, RepairOrderStatus.AWAITING_APPROVAL,
                RepairOrderStatus.AWAITING_PARTS, RepairOrderStatus.IN_PROGRESS,
                RepairOrderStatus.CANCELLED, RepairOrderStatus.IRREPARABLE));
        TRANSITIONS.put(RepairOrderStatus.DIAGNOSING, EnumSet.of(
                RepairOrderStatus.AWAITING_APPROVAL, RepairOrderStatus.AWAITING_PARTS,
                RepairOrderStatus.IN_PROGRESS, RepairOrderStatus.CANCELLED,
                RepairOrderStatus.IRREPARABLE));
        TRANSITIONS.put(RepairOrderStatus.AWAITING_APPROVAL, EnumSet.of(
                RepairOrderStatus.AWAITING_PARTS, RepairOrderStatus.IN_PROGRESS,
                RepairOrderStatus.CANCELLED, RepairOrderStatus.IRREPARABLE));
        TRANSITIONS.put(RepairOrderStatus.AWAITING_PARTS, EnumSet.of(
                RepairOrderStatus.IN_PROGRESS, RepairOrderStatus.CANCELLED));
        TRANSITIONS.put(RepairOrderStatus.IN_PROGRESS, EnumSet.of(
                RepairOrderStatus.COMPLETED, RepairOrderStatus.AWAITING_PARTS,
                RepairOrderStatus.CANCELLED, RepairOrderStatus.IRREPARABLE));
        TRANSITIONS.put(RepairOrderStatus.COMPLETED, EnumSet.of(
                RepairOrderStatus.DELIVERED, RepairOrderStatus.CANCELLED));
        TRANSITIONS.put(RepairOrderStatus.IRREPARABLE, EnumSet.of(
                RepairOrderStatus.DELIVERED, RepairOrderStatus.CANCELLED));
        TRANSITIONS.put(RepairOrderStatus.DELIVERED, EnumSet.noneOf(RepairOrderStatus.class));
        TRANSITIONS.put(RepairOrderStatus.CANCELLED, EnumSet.noneOf(RepairOrderStatus.class));
    }

    private final RepairOrderRepository repairOrderRepository;
    private final ItemRepository itemRepository;
    private final ItemSerialRepository itemSerialRepository;
    private final CustomerRepository customerRepository;
    private final TaxRateRepository taxRateRepository;
    private final PaymentRepository paymentRepository;
    private final DocumentNumberService documentNumberService;
    private final StockLedgerService stockLedgerService;

    public RepairService(RepairOrderRepository repairOrderRepository,
                         ItemRepository itemRepository,
                         ItemSerialRepository itemSerialRepository,
                         CustomerRepository customerRepository,
                         TaxRateRepository taxRateRepository,
                         PaymentRepository paymentRepository,
                         DocumentNumberService documentNumberService,
                         StockLedgerService stockLedgerService) {
        this.repairOrderRepository = repairOrderRepository;
        this.itemRepository = itemRepository;
        this.itemSerialRepository = itemSerialRepository;
        this.customerRepository = customerRepository;
        this.taxRateRepository = taxRateRepository;
        this.paymentRepository = paymentRepository;
        this.documentNumberService = documentNumberService;
        this.stockLedgerService = stockLedgerService;
    }

    @Transactional(readOnly = true)
    public PageResponse<RepairResponse> list(String q, RepairOrderStatus status, UUID customerId,
                                             Pageable pageable) {
        return PageResponse.of(
                repairOrderRepository.search(blankToNull(q), status, customerId, pageable)
                        .map(RepairMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public RepairResponse get(UUID id) {
        return RepairMapper.toResponse(requireWithLines(id));
    }

    @Transactional
    public RepairResponse create(CreateRepairRequest request) {
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            var existing = repairOrderRepository.findByIdempotencyKey(request.idempotencyKey().trim());
            if (existing.isPresent()) {
                return RepairMapper.toResponse(requireWithLines(existing.get().getId()));
            }
        }

        RepairOrder order = new RepairOrder();
        order.setOutletId(TenantContext.requireOutletId());
        order.setRepairNumber(documentNumberService.next(DocumentType.REPAIR_ORDER));
        order.setStatus(RepairOrderStatus.RECEIVED);
        order.setReceivedAt(Instant.now());
        order.setCreatedBy(TenantContext.userIdOrNull());
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            order.setIdempotencyKey(request.idempotencyKey().trim());
        }
        applyCustomer(order, request.customerId(), request.customerName(), request.customerPhone());
        applyDeviceAndMeta(order, request);
        order.setServiceCharge(Money.of(request.serviceCharge()));
        order.setEstimatedCost(request.estimatedCost() == null ? null : Money.of(request.estimatedCost()));
        order.setWarrantyDays(request.warrantyDays() == null ? 0 : request.warrantyDays());
        order.setPromisedAt(request.promisedAt());
        order.setTechnicianId(request.technicianId());
        order.setNote(blankToNull(request.note()));
        order.setAdvancePaid(Money.of(request.advancePaid()));
        order.setAmountPaid(order.getAdvancePaid());
        order.setDeviceConditions(toArray(request.deviceConditions()));
        order.setBorrowedItems(toArray(request.borrowedItems()));

        if (request.lines() != null) {
            short n = 1;
            for (RepairLineRequest lineRequest : request.lines()) {
                order.addLine(buildLine(lineRequest, n++));
            }
        }

        recalculate(order);
        order.addHistory(RepairHistory.of(null, RepairOrderStatus.RECEIVED, "Created", TenantContext.userIdOrNull()));
        order = repairOrderRepository.save(order);

        if (Money.isPositive(order.getAdvancePaid())) {
            recordPayment(order, PaymentMethod.CASH, order.getAdvancePaid(), null, null, null, null,
                    null, null, "Advance at intake");
        }

        return RepairMapper.toResponse(order);
    }

    @Transactional
    public RepairResponse update(UUID id, UpdateRepairRequest request) {
        RepairOrder order = requireWithLines(id);
        if (!order.isEditable()) {
            throw ApiException.of(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Repair cannot be updated in status " + order.getStatus());
        }
        if (request.customerId() != null || request.customerName() != null || request.customerPhone() != null) {
            applyCustomer(order,
                    request.customerId() != null ? request.customerId() : order.getCustomerId(),
                    request.customerName() != null ? request.customerName() : order.getCustomerName(),
                    request.customerPhone() != null ? request.customerPhone() : order.getCustomerPhone());
        }
        if (request.deviceType() != null) {
            order.setDeviceType(blankToNull(request.deviceType()));
        }
        if (request.deviceBrand() != null) {
            order.setDeviceBrand(blankToNull(request.deviceBrand()));
        }
        if (request.deviceModel() != null) {
            order.setDeviceModel(blankToNull(request.deviceModel()));
        }
        if (request.deviceSerial() != null) {
            order.setDeviceSerial(blankToNull(request.deviceSerial()));
        }
        if (request.repairType() != null) {
            order.setRepairType(blankToNull(request.repairType()));
        }
        if (request.reportedFault() != null) {
            order.setReportedFault(request.reportedFault());
        }
        if (request.diagnosis() != null) {
            order.setDiagnosis(request.diagnosis());
        }
        if (request.deviceConditions() != null) {
            order.setDeviceConditions(toArray(request.deviceConditions()));
        }
        if (request.borrowedItems() != null) {
            order.setBorrowedItems(toArray(request.borrowedItems()));
        }
        if (request.accessoriesNote() != null) {
            order.setAccessoriesNote(request.accessoriesNote());
        }
        if (request.devicePasscode() != null) {
            order.setDevicePasscode(blankToNull(request.devicePasscode()));
        }
        if (request.serviceCharge() != null) {
            order.setServiceCharge(Money.of(request.serviceCharge()));
        }
        if (request.discountAmount() != null) {
            order.setDiscountAmount(Money.of(request.discountAmount()));
        }
        if (request.estimatedCost() != null) {
            order.setEstimatedCost(Money.of(request.estimatedCost()));
        }
        if (request.warrantyDays() != null) {
            order.setWarrantyDays(request.warrantyDays());
        }
        if (request.promisedAt() != null) {
            order.setPromisedAt(request.promisedAt());
        }
        if (request.technicianId() != null) {
            order.setTechnicianId(request.technicianId());
        }
        if (request.note() != null) {
            order.setNote(blankToNull(request.note()));
        }
        order.setUpdatedBy(TenantContext.userIdOrNull());
        recalculate(order);
        return RepairMapper.toResponse(repairOrderRepository.save(order));
    }

    @Transactional
    public RepairResponse addLine(UUID id, RepairLineRequest request) {
        RepairOrder order = requireWithLines(id);
        if (!order.isEditable()) {
            throw ApiException.of(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot add parts in status " + order.getStatus());
        }
        short next = (short) (order.getLines().stream().mapToInt(RepairLine::getLineNumber).max().orElse(0) + 1);
        order.addLine(buildLine(request, next));
        order.setUpdatedBy(TenantContext.userIdOrNull());
        recalculate(order);
        return RepairMapper.toResponse(repairOrderRepository.save(order));
    }

    @Transactional
    public RepairResponse transition(UUID id, TransitionRequest request) {
        RepairOrder order = requireWithLines(id);
        RepairOrderStatus from = order.getStatus();
        RepairOrderStatus to = request.status();
        if (from == to) {
            return RepairMapper.toResponse(order);
        }
        Set<RepairOrderStatus> allowed = TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw ApiException.of(ErrorCode.INVALID_STATUS_TRANSITION,
                            "Cannot transition repair from " + from + " to " + to)
                    .with("from", from.name())
                    .with("to", to.name());
        }

        if (to == RepairOrderStatus.DELIVERED) {
            if (from != RepairOrderStatus.COMPLETED && from != RepairOrderStatus.IRREPARABLE) {
                throw ApiException.of(ErrorCode.REPAIR_NOT_DELIVERABLE,
                        "Repair must be completed (or irreparable) before delivery");
            }
            if (from == RepairOrderStatus.COMPLETED && Money.isPositive(order.getBalanceDue())) {
                throw ApiException.of(ErrorCode.REPAIR_NOT_DELIVERABLE,
                                "Outstanding balance must be settled before delivery")
                        .with("balanceDue", order.getBalanceDue());
            }
        }

        if (to == RepairOrderStatus.CANCELLED) {
            order.setCancelledAt(Instant.now());
            order.setCancelReason(blankToNull(request.cancelReason()));
            if (order.partsWereDeducted()) {
                restorePartsStock(order);
            }
        }

        if (to == RepairOrderStatus.COMPLETED) {
            deductPartsStock(order);
            order.setCompletedAt(Instant.now());
            if (order.getWarrantyDays() > 0) {
                order.setWarrantyEndsOn(LocalDate.now().plusDays(order.getWarrantyDays()));
            }
        }

        if (to == RepairOrderStatus.DELIVERED) {
            order.setDeliveredAt(Instant.now());
        }

        order.setStatus(to);
        order.setUpdatedBy(TenantContext.userIdOrNull());
        order.addHistory(RepairHistory.of(from, to, request.note(), TenantContext.userIdOrNull()));
        return RepairMapper.toResponse(repairOrderRepository.save(order));
    }

    @Transactional
    public RepairResponse collectPayment(UUID id, PaymentRequest request) {
        RepairOrder order = requireWithLines(id);
        if (order.getStatus() == RepairOrderStatus.CANCELLED) {
            throw ApiException.of(ErrorCode.INVALID_STATUS_TRANSITION, "Cannot collect on a cancelled repair");
        }
        if (!Money.isPositive(order.getBalanceDue())) {
            throw ApiException.of(ErrorCode.PAYMENT_EXCEEDS_BALANCE, "Repair has no balance due");
        }
        BigDecimal applied = Money.min(Money.of(request.amount()), order.getBalanceDue());
        recordPayment(order, request.method(), applied, request.tenderedAmount(), request.reference(),
                request.cardLast4(), request.bankName(), request.chequeNumber(), request.chequeDate(),
                request.note());
        order.setAmountPaid(Money.add(order.getAmountPaid(), applied));
        recalculate(order);
        order.setUpdatedBy(TenantContext.userIdOrNull());
        return RepairMapper.toResponse(repairOrderRepository.save(order));
    }

    @Transactional
    public RepairResponse refund(UUID id, RefundRequest request) {
        RepairOrder order = requireWithLines(id);
        if (order.getStatus() == RepairOrderStatus.CANCELLED) {
            throw ApiException.of(ErrorCode.INVALID_STATUS_TRANSITION, "Repair is already cancelled");
        }
        BigDecimal amount = Money.of(request.amount());
        if (Money.gt(amount, order.getAmountPaid())) {
            throw ApiException.of(ErrorCode.REFUND_EXCEEDS_BILL, "Refund exceeds amount paid")
                    .with("amountPaid", order.getAmountPaid())
                    .with("requested", amount);
        }

        Payment payment = new Payment();
        payment.setOutletId(order.getOutletId());
        payment.setDocumentType(PaymentDocumentType.REPAIR_ORDER);
        payment.setDocumentId(order.getId());
        payment.setCustomerId(order.getCustomerId());
        payment.setMethod(request.method());
        payment.setDirection(PaymentDirection.OUT);
        payment.setAmount(amount);
        payment.setCurrency(order.getCurrency());
        payment.setNote(blankToNull(request.reason()));
        payment.setReceivedAt(Instant.now());
        payment.setCreatedBy(TenantContext.userIdOrNull());
        paymentRepository.save(payment);

        order.setAmountPaid(Money.subtract(order.getAmountPaid(), amount));
        if (request.restoreParts() && order.partsWereDeducted()) {
            restorePartsStock(order);
            order.setCompletedAt(null);
            if (order.getStatus() == RepairOrderStatus.COMPLETED
                    || order.getStatus() == RepairOrderStatus.DELIVERED) {
                RepairOrderStatus from = order.getStatus();
                order.setStatus(RepairOrderStatus.IN_PROGRESS);
                order.setDeliveredAt(null);
                order.addHistory(RepairHistory.of(from, RepairOrderStatus.IN_PROGRESS,
                        "Parts restored on refund", TenantContext.userIdOrNull()));
            }
        }
        recalculate(order);
        order.setUpdatedBy(TenantContext.userIdOrNull());
        return RepairMapper.toResponse(repairOrderRepository.save(order));
    }

    private void deductPartsStock(RepairOrder order) {
        if (order.getCompletedAt() != null) {
            return;
        }
        for (RepairLine line : order.getLines()) {
            if (!line.isPart() || line.getItemId() == null) {
                continue;
            }
            stockLedgerService.sell(
                    line.getItemId(),
                    line.getQuantity(),
                    "REPAIR_ORDER",
                    order.getId(),
                    order.getRepairNumber());
            if (!line.getSerials().isEmpty()) {
                List<UUID> serialIds = line.getSerials().stream()
                        .map(RepairLineSerial::getItemSerialId)
                        .toList();
                stockLedgerService.markSerialsSold(
                        serialIds, SoldDocumentType.REPAIR_ORDER, order.getId(), order.getRepairNumber());
            }
        }
    }

    private void restorePartsStock(RepairOrder order) {
        for (RepairLine line : order.getLines()) {
            if (!line.isPart() || line.getItemId() == null) {
                continue;
            }
            stockLedgerService.restore(
                    line.getItemId(),
                    line.getQuantity(),
                    "REPAIR_ORDER",
                    order.getId(),
                    order.getRepairNumber());
        }
        stockLedgerService.returnSerials(SoldDocumentType.REPAIR_ORDER, order.getId());
    }

    private RepairLine buildLine(RepairLineRequest request, short lineNumber) {
        RepairLineType type = request.lineType() == null ? RepairLineType.PART : request.lineType();
        RepairLine line = new RepairLine();
        line.setLineNumber(lineNumber);
        line.setLineType(type);
        line.setDescription(request.description().trim());
        line.setQuantity(Money.quantity(request.quantity()));
        line.setDiscountType(request.discountType() == null ? DiscountType.NONE : request.discountType());
        line.setDiscountInput(Money.of(request.discountInput()));
        line.setWarrantyLabel(request.warrantyLabel());

        Item item = null;
        if (type == RepairLineType.PART) {
            if (request.itemId() == null) {
                throw ApiException.validation("PART lines require itemId");
            }
            item = itemRepository.findByIdAndDeletedAtIsNull(request.itemId())
                    .orElseThrow(() -> ApiException.notFound("Item", request.itemId()));
            if (!item.isSellable()) {
                throw ApiException.of(ErrorCode.ITEM_NOT_SELLABLE, "Item is not sellable")
                        .with("itemId", item.getId());
            }
            line.setItemId(item.getId());
            line.setItemSku(item.getSku());
            line.setUnitCost(Money.of(item.getCostPrice()));
            line.setUnitPrice(request.unitPrice() != null
                    ? Money.of(request.unitPrice())
                    : Money.of(item.getRetailPrice()));
            line.setTaxRateId(request.taxRateId() != null ? request.taxRateId() : item.getTaxRateId());
            if (request.warrantyLabel() == null) {
                line.setWarrantyLabel(item.getWarrantyLabel());
            }
        } else {
            line.setItemId(request.itemId());
            line.setUnitPrice(Money.of(request.unitPrice()));
            line.setTaxRateId(request.taxRateId());
            if (request.itemId() != null) {
                item = itemRepository.findByIdAndDeletedAtIsNull(request.itemId()).orElse(null);
                if (item != null) {
                    line.setItemSku(item.getSku());
                    line.setUnitCost(Money.of(item.getCostPrice()));
                }
            }
        }

        TaxRate taxRate = resolveTax(line.getTaxRateId());
        BigDecimal taxPercent = taxRate != null ? taxRate.getRatePercent() : Money.ZERO;
        boolean inclusive = taxRate != null && taxRate.isInclusive();
        line.setTaxRatePercent(taxPercent);

        SalesPricing.LineTotals totals = SalesPricing.computeLine(
                line.getQuantity(),
                line.getUnitPrice(),
                line.getDiscountType(),
                line.getDiscountInput(),
                taxPercent,
                inclusive);
        line.setGrossAmount(totals.grossAmount());
        line.setDiscountAmount(totals.discountAmount());
        line.setNetAmount(totals.netAmount());
        line.setTaxAmount(totals.taxAmount());
        line.setLineTotal(totals.lineTotal());

        if (item != null && item.isHasSerialTracking()) {
            List<UUID> serialIds = request.serialIds() == null ? List.of() : request.serialIds();
            int expected = line.getQuantity().intValueExact();
            if (serialIds.size() != expected) {
                throw ApiException.of(ErrorCode.SERIAL_COUNT_MISMATCH,
                                "Serialised parts require one serial per unit")
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
                                "Serial not available for this part");
                    }
                    RepairLineSerial rls = new RepairLineSerial();
                    rls.setItemSerialId(serial.getId());
                    rls.setSerialNumber(serial.getSerialNumber());
                    line.addSerial(rls);
                }
            }
        }
        return line;
    }

    private void recalculate(RepairOrder order) {
        BigDecimal partsSubtotal = Money.ZERO;
        BigDecimal lineDiscount = Money.ZERO;
        BigDecimal taxTotal = Money.ZERO;
        BigDecimal linesGrand = Money.ZERO;
        BigDecimal partsCost = Money.ZERO;

        for (RepairLine line : order.getLines()) {
            if (line.isPart()) {
                partsSubtotal = Money.add(partsSubtotal, line.getGrossAmount());
                partsCost = Money.add(partsCost, Money.of(line.getQuantity().multiply(line.getUnitCost())));
            }
            lineDiscount = Money.add(lineDiscount, line.getDiscountAmount());
            taxTotal = Money.add(taxTotal, line.getTaxAmount());
            linesGrand = Money.add(linesGrand, line.getLineTotal());
        }

        BigDecimal serviceCharge = Money.of(order.getServiceCharge());
        BigDecimal headerDiscount = Money.of(order.getDiscountAmount());
        BigDecimal grand = Money.subtract(Money.add(linesGrand, serviceCharge), headerDiscount);
        if (grand.signum() < 0) {
            grand = Money.ZERO;
        }

        order.setPartsSubtotal(partsSubtotal);
        order.setLineDiscountTotal(lineDiscount);
        order.setTaxTotal(taxTotal);
        order.setPartsCost(partsCost);
        order.setGrandTotal(grand);
        order.setBalanceDue(Money.subtract(grand, Money.of(order.getAmountPaid())));
    }

    private void recordPayment(RepairOrder order,
                               PaymentMethod method,
                               BigDecimal amount,
                               BigDecimal tenderedAmount,
                               String reference,
                               String cardLast4,
                               String bankName,
                               String chequeNumber,
                               java.time.LocalDate chequeDate,
                               String note) {
        if (method == PaymentMethod.CHEQUE && (chequeNumber == null || chequeDate == null)) {
            throw ApiException.validation("Cheque payments require chequeNumber and chequeDate");
        }
        Payment payment = new Payment();
        payment.setOutletId(order.getOutletId());
        payment.setDocumentType(PaymentDocumentType.REPAIR_ORDER);
        payment.setDocumentId(order.getId());
        payment.setCustomerId(order.getCustomerId());
        payment.setMethod(method);
        payment.setDirection(PaymentDirection.IN);
        payment.setAmount(amount);
        payment.setCurrency(order.getCurrency());
        payment.setTenderedAmount(tenderedAmount == null ? null : Money.of(tenderedAmount));
        payment.setChangeAmount(method == PaymentMethod.CASH && payment.getTenderedAmount() != null
                ? Money.max(Money.subtract(payment.getTenderedAmount(), amount), Money.ZERO)
                : Money.ZERO);
        payment.setReference(reference);
        payment.setCardLast4(cardLast4);
        payment.setBankName(bankName);
        payment.setChequeNumber(chequeNumber);
        payment.setChequeDate(chequeDate);
        if (method == PaymentMethod.CHEQUE) {
            payment.setChequeStatus("PENDING");
        }
        payment.setNote(note);
        payment.setReceivedAt(Instant.now());
        payment.setCreatedBy(TenantContext.userIdOrNull());
        paymentRepository.save(payment);
    }

    private void applyCustomer(RepairOrder order, UUID customerId, String name, String phone) {
        if (customerId != null) {
            Customer customer = customerRepository.findByIdAndDeletedAtIsNull(customerId)
                    .orElseThrow(() -> ApiException.notFound("Customer", customerId));
            order.setCustomerId(customer.getId());
            order.setCustomerName(name != null && !name.isBlank() ? name.trim() : customer.getDisplayName());
            order.setCustomerPhone(phone != null ? phone : customer.getPhonePrimary());
        } else {
            order.setCustomerId(null);
            order.setCustomerName(name == null || name.isBlank() ? "Walk-in Customer" : name.trim());
            order.setCustomerPhone(phone);
        }
    }

    private void applyDeviceAndMeta(RepairOrder order, CreateRepairRequest request) {
        order.setDeviceType(blankToNull(request.deviceType()));
        order.setDeviceBrand(blankToNull(request.deviceBrand()));
        order.setDeviceModel(blankToNull(request.deviceModel()));
        order.setDeviceSerial(blankToNull(request.deviceSerial()));
        order.setRepairType(blankToNull(request.repairType()));
        order.setReportedFault(request.reportedFault());
        order.setDiagnosis(request.diagnosis());
        order.setAccessoriesNote(request.accessoriesNote());
        order.setDevicePasscode(blankToNull(request.devicePasscode()));
    }

    private RepairOrder requireWithLines(UUID id) {
        return repairOrderRepository.findByIdWithLines(id)
                .orElseThrow(() -> ApiException.notFound("RepairOrder", id));
    }

    private TaxRate resolveTax(UUID taxRateId) {
        if (taxRateId == null) {
            return null;
        }
        return taxRateRepository.findById(taxRateId).orElse(null);
    }

    private static String[] toArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return new String[0];
        }
        return values.stream().map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
