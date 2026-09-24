package com.possaas.sales.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.possaas.common.api.PageResponse;
import com.possaas.common.audit.AuditService;
import com.possaas.common.audit.AuditSeverity;
import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.money.Money;
import com.possaas.common.tenant.TenantContext;
import com.possaas.sales.domain.CashDrawer;
import com.possaas.sales.domain.CashMovement;
import com.possaas.sales.domain.CashMovementType;
import com.possaas.sales.domain.PaymentDocumentType;
import com.possaas.sales.domain.Shift;
import com.possaas.sales.domain.ShiftStatus;
import com.possaas.sales.dto.ShiftDtos.CashMovementRequest;
import com.possaas.sales.dto.ShiftDtos.CashMovementResponse;
import com.possaas.sales.dto.ShiftDtos.CloseShiftRequest;
import com.possaas.sales.dto.ShiftDtos.OpenShiftRequest;
import com.possaas.sales.dto.ShiftDtos.ShiftReport;
import com.possaas.sales.repository.CashMovementRepository;
import com.possaas.sales.repository.PaymentRepository;
import com.possaas.sales.repository.ShiftRepository;
import com.possaas.tenancy.domain.DocumentType;
import com.possaas.tenancy.service.DocumentNumberService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cashier shifts and the drawer reconciliation behind the X and Z reports.
 *
 * <p>Expected cash is always derived from the payments and movements recorded
 * during the shift, never accumulated into a running column, so it cannot drift
 * away from the documents that produced it. It is only written down at close,
 * where freezing it is the point.
 */
@Service
public class ShiftService {

    private final ShiftRepository shiftRepository;
    private final CashMovementRepository cashMovementRepository;
    private final PaymentRepository paymentRepository;
    private final DocumentNumberService documentNumberService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ShiftService(ShiftRepository shiftRepository,
                        CashMovementRepository cashMovementRepository,
                        PaymentRepository paymentRepository,
                        DocumentNumberService documentNumberService,
                        AuditService auditService,
                        ObjectMapper objectMapper) {
        this.shiftRepository = shiftRepository;
        this.cashMovementRepository = cashMovementRepository;
        this.paymentRepository = paymentRepository;
        this.documentNumberService = documentNumberService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ShiftReport open(OpenShiftRequest request) {
        UUID outletId = request.outletId() != null
                ? request.outletId()
                : TenantContext.requireOutletId();

        if (shiftRepository.existsByOutletIdAndStatus(outletId, ShiftStatus.OPEN)) {
            throw ApiException.of(ErrorCode.SHIFT_ALREADY_OPEN,
                            "This till already has an open shift; close it before opening another")
                    .with("outletId", outletId);
        }

        Shift shift = new Shift();
        shift.setOutletId(outletId);
        shift.setShiftNumber(documentNumberService.next(DocumentType.SHIFT));
        shift.setOpeningFloat(Money.of(request.openingFloat()));
        shift.setOpenedBy(TenantContext.userIdOrNull());
        shift.setOpenedAt(Instant.now());
        shift.setNote(blankToNull(request.note()));
        Shift saved = shiftRepository.save(shift);

        auditService.record("SHIFT", saved.getId(), saved.getShiftNumber(), "OPEN",
                AuditSeverity.INFO,
                "Shift " + saved.getShiftNumber() + " opened with float " + saved.getOpeningFloat(),
                Map.of("openingFloat", saved.getOpeningFloat()), null);

        return report(saved);
    }

    /** The X-report: where the drawer stands right now, without freezing anything. */
    @Transactional(readOnly = true)
    public ShiftReport get(UUID id) {
        return report(require(id));
    }

    @Transactional(readOnly = true)
    public ShiftReport current(UUID outletId) {
        UUID till = outletId != null ? outletId : TenantContext.requireOutletId();
        Shift shift = shiftRepository.findByOutletIdAndStatus(till, ShiftStatus.OPEN)
                .orElseThrow(() -> ApiException.of(ErrorCode.SHIFT_NOT_OPEN,
                        "No open shift at this till"));
        return report(shift);
    }

    @Transactional(readOnly = true)
    public PageResponse<ShiftReport> list(ShiftStatus status, Pageable pageable) {
        return PageResponse.of(shiftRepository.search(status, pageable), this::report);
    }

    @Transactional
    public ShiftReport recordMovement(UUID id, CashMovementRequest request) {
        Shift shift = require(id);
        requireOpen(shift);

        CashMovement movement = new CashMovement();
        movement.setShiftId(shift.getId());
        movement.setOutletId(shift.getOutletId());
        movement.setMovementType(request.movementType());
        movement.setAmount(Money.of(request.amount()));
        movement.setReason(blankToNull(request.reason()));
        movement.setReference(blankToNull(request.reference()));
        movement.setOccurredAt(Instant.now());
        movement.setCreatedBy(TenantContext.userIdOrNull());
        cashMovementRepository.save(movement);

        auditService.record("SHIFT", shift.getId(), shift.getShiftNumber(),
                request.movementType().name(), AuditSeverity.INFO,
                request.movementType() + " of " + movement.getAmount()
                        + " on shift " + shift.getShiftNumber()
                        + (movement.getReason() != null ? ": " + movement.getReason() : ""),
                Map.of("amount", movement.getAmount(), "type", request.movementType().name()), null);

        return report(shift);
    }

    /** The Z-report: records the physical count, posts the variance, freezes the shift. */
    @Transactional
    public ShiftReport close(UUID id, CloseShiftRequest request) {
        Shift shift = require(id);
        requireOpen(shift);

        CashDrawer drawer = drawerFor(shift);
        BigDecimal counted = Money.of(request.countedCash());
        shift.setDenominations(serializeDenominations(request.denominations()));
        if (request.note() != null && !request.note().isBlank()) {
            shift.setNote(request.note().trim());
        }
        shift.close(drawer.expectedCash(), counted, TenantContext.userIdOrNull());
        Shift saved = shiftRepository.save(shift);

        // A drawer that doesn't balance is the thing a manager needs to see, so it
        // is logged louder than a clean close.
        boolean balanced = saved.getVariance().signum() == 0;
        auditService.record("SHIFT", saved.getId(), saved.getShiftNumber(), "CLOSE",
                balanced ? AuditSeverity.INFO : AuditSeverity.WARN,
                "Shift " + saved.getShiftNumber() + " closed. Expected " + saved.getExpectedCash()
                        + ", counted " + saved.getCountedCash()
                        + ", variance " + saved.getVariance(),
                Map.of("expectedCash", saved.getExpectedCash(),
                        "countedCash", saved.getCountedCash(),
                        "variance", saved.getVariance()), null);

        return report(saved);
    }

    // --- drawer arithmetic ----------------------------------------------------

    private CashDrawer drawerFor(Shift shift) {
        // An open shift runs up to now; a closed one is fixed to the moment it closed,
        // so a Z-report stays the same however long after the fact it is read.
        Instant until = shift.getClosedAt() != null ? shift.getClosedAt() : Instant.now();

        Map<PaymentDocumentType, BigDecimal> cashIn = new EnumMap<>(PaymentDocumentType.class);
        for (Object[] row : paymentRepository.sumCashInByDocumentType(
                shift.getOutletId(), shift.getOpenedAt(), until)) {
            cashIn.put((PaymentDocumentType) row[0], Money.of((BigDecimal) row[1]));
        }
        BigDecimal cashSales = cashIn.getOrDefault(PaymentDocumentType.BILL, Money.ZERO);
        BigDecimal cashRepairs = cashIn.getOrDefault(PaymentDocumentType.REPAIR_ORDER, Money.ZERO);
        BigDecimal cashWholesale =
                cashIn.getOrDefault(PaymentDocumentType.WHOLESALE_INVOICE, Money.ZERO);
        BigDecimal cashRefunds = Money.of(
                paymentRepository.sumCashOut(shift.getOutletId(), shift.getOpenedAt(), until));

        BigDecimal payIns = Money.ZERO;
        BigDecimal payouts = Money.ZERO;
        BigDecimal drops = Money.ZERO;
        for (CashMovement movement : cashMovementRepository
                .findByShiftIdOrderByOccurredAtAsc(shift.getId())) {
            switch (movement.getMovementType()) {
                case PAY_IN -> payIns = Money.add(payIns, movement.getAmount());
                case PAYOUT -> payouts = Money.add(payouts, movement.getAmount());
                case DROP -> drops = Money.add(drops, movement.getAmount());
            }
        }

        return CashDrawer.of(shift.getOpeningFloat(), cashSales, cashRepairs, cashWholesale,
                cashRefunds, payIns, payouts, drops);
    }

    private ShiftReport report(Shift shift) {
        CashDrawer drawer = drawerFor(shift);
        List<CashMovementResponse> movements = cashMovementRepository
                .findByShiftIdOrderByOccurredAtAsc(shift.getId()).stream()
                .map(m -> new CashMovementResponse(m.getId(), m.getMovementType(), m.getAmount(),
                        m.getReason(), m.getReference(), m.getOccurredAt()))
                .toList();

        // A closed shift reports the figures frozen at close, not a fresh calculation.
        BigDecimal expected = shift.getExpectedCash() != null
                ? shift.getExpectedCash()
                : drawer.expectedCash();

        return new ShiftReport(
                shift.getId(),
                shift.getShiftNumber(),
                shift.getStatus(),
                shift.getOutletId(),
                shift.getOpenedAt(),
                shift.getClosedAt(),
                shift.getOpeningFloat(),
                drawer.cashSales(),
                drawer.cashRepairs(),
                drawer.cashWholesale(),
                drawer.cashRefunds(),
                drawer.payIns(),
                drawer.payouts(),
                drawer.drops(),
                expected,
                shift.getCountedCash(),
                shift.getVariance(),
                movements);
    }

    private String serializeDenominations(Map<String, Integer> denominations) {
        if (denominations == null || denominations.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(denominations);
        } catch (JsonProcessingException ex) {
            throw ApiException.validation("Could not read the denomination breakdown");
        }
    }

    private Shift require(UUID id) {
        return shiftRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Shift", id));
    }

    private static void requireOpen(Shift shift) {
        if (!shift.isOpen()) {
            throw ApiException.of(ErrorCode.SHIFT_ALREADY_CLOSED,
                            "Shift " + shift.getShiftNumber() + " is already closed")
                    .with("shiftNumber", shift.getShiftNumber());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
