package com.possaas.catalog.service;

import com.possaas.catalog.api.dto.CatalogDtos.GrnCreateRequest;
import com.possaas.catalog.api.dto.CatalogDtos.GrnLineRequest;
import com.possaas.catalog.api.dto.CatalogDtos.GrnResponse;
import com.possaas.catalog.api.dto.CatalogDtos.GrnUnitRequest;
import com.possaas.catalog.domain.GoodsReceivedNote;
import com.possaas.catalog.domain.GrnLine;
import com.possaas.catalog.domain.Imei;
import com.possaas.catalog.domain.Item;
import com.possaas.catalog.domain.ItemSerial;
import com.possaas.catalog.domain.SerialStatus;
import com.possaas.catalog.domain.UnitCondition;
import com.possaas.catalog.repository.GoodsReceivedNoteRepository;
import com.possaas.catalog.repository.GrnLineRepository;
import com.possaas.catalog.repository.ItemRepository;
import com.possaas.catalog.repository.ItemSerialRepository;
import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.money.Money;
import com.possaas.tenancy.domain.DocumentType;
import com.possaas.tenancy.service.DocumentNumberService;
import com.possaas.tenancy.service.TenantService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GrnService {

    private final GoodsReceivedNoteRepository grnRepository;
    private final GrnLineRepository grnLineRepository;
    private final ItemRepository itemRepository;
    private final ItemSerialRepository itemSerialRepository;
    private final StockLedgerService stockLedgerService;
    private final DocumentNumberService documentNumberService;
    private final TenantService tenantService;
    private final SupplierService supplierService;

    public GrnService(GoodsReceivedNoteRepository grnRepository,
                      GrnLineRepository grnLineRepository,
                      ItemRepository itemRepository,
                      ItemSerialRepository itemSerialRepository,
                      StockLedgerService stockLedgerService,
                      DocumentNumberService documentNumberService,
                      TenantService tenantService,
                      SupplierService supplierService) {
        this.grnRepository = grnRepository;
        this.grnLineRepository = grnLineRepository;
        this.itemRepository = itemRepository;
        this.itemSerialRepository = itemSerialRepository;
        this.stockLedgerService = stockLedgerService;
        this.documentNumberService = documentNumberService;
        this.tenantService = tenantService;
        this.supplierService = supplierService;
    }

    @Transactional(readOnly = true)
    public Page<GrnResponse> list(Pageable pageable) {
        return grnRepository.findAllByOrderByReceivedAtDesc(pageable).map(grn -> toResponse(grn, false));
    }

    @Transactional(readOnly = true)
    public GrnResponse get(UUID id) {
        GoodsReceivedNote grn = grnRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("GoodsReceivedNote", id));
        return toResponse(grn, true);
    }

    /**
     * Creates a GRN with lines and optional serials, posts stock in the same transaction.
     */
    @Transactional
    public GrnResponse createAndPost(GrnCreateRequest request) {
        if (request.supplierId() != null) {
            supplierService.require(request.supplierId());
        }
        UUID outletId = tenantService.resolveOutlet(request.outletId()).getId();

        GoodsReceivedNote grn = new GoodsReceivedNote();
        grn.setOutletId(outletId);
        grn.setGrnNumber(documentNumberService.next(DocumentType.GRN));
        grn.setSupplierId(request.supplierId());
        grn.setSupplierInvoiceNo(blankToNull(request.supplierInvoiceNo()));
        grn.setNotes(blankToNull(request.notes()));
        grn.setReceivedAt(request.receivedAt() == null ? Instant.now() : request.receivedAt());
        grn = grnRepository.save(grn);

        BigDecimal subtotal = Money.ZERO;
        short lineNumber = 1;
        List<GrnLine> lines = new ArrayList<>();
        List<ItemSerial> serials = new ArrayList<>();

        for (GrnLineRequest lineRequest : request.lines()) {
            Item item = itemRepository.findByIdAndDeletedAtIsNull(lineRequest.itemId())
                    .orElseThrow(() -> ApiException.notFound("Item", lineRequest.itemId()));

            BigDecimal quantity = Money.quantity(lineRequest.quantity());
            BigDecimal unitCost = Money.of(lineRequest.unitCost());
            BigDecimal lineTotal = Money.multiply(unitCost, quantity);

            GrnLine line = new GrnLine();
            line.setGrnId(grn.getId());
            line.setItemId(item.getId());
            line.setLineNumber(lineNumber++);
            line.setQuantity(quantity);
            line.setUnitCost(unitCost);
            line.setLineTotal(lineTotal);
            line.setRetailPriceAtReceipt(lineRequest.retailPriceAtReceipt() == null
                    ? item.getRetailPrice()
                    : Money.of(lineRequest.retailPriceAtReceipt()));
            short warrantyMonths = lineRequest.warrantyMonths() == null
                    ? item.getWarrantyMonths()
                    : lineRequest.warrantyMonths();
            line.setWarrantyMonths(warrantyMonths);
            lines.add(line);

            List<GrnUnitRequest> units = resolveUnits(item, lineRequest);
            if (item.isHasSerialTracking()) {
                validateSerialCount(item, quantity, units);
                serials.addAll(createSerials(grn, item, outletId, unitCost, warrantyMonths, units));
            } else if (!units.isEmpty()) {
                throw ApiException.validation("Serial numbers supplied for a non-serialised item")
                        .with("itemId", item.getId());
            }

            BigDecimal previousQuantity = item.getQuantityOnHand();
            BigDecimal previousCost = item.getCostPrice();

            stockLedgerService.receiveGrn(
                    item.getId(), quantity, unitCost, "GRN", grn.getId());

            // Weighted-average cost, not a flat overwrite: this item's SKU still has
            // units on hand from whatever was bought before this receipt, so blending
            // the two costs (rather than replacing the cost with just the new
            // receipt's price) avoids silently revaluing older stock to the new price.
            // This isn't full FIFO/lot costing - that needs a real stock-lots table -
            // but it's a correct blended cost rather than a wrong one.
            item.setCostPrice(previousQuantity.signum() <= 0 || previousCost == null
                    ? unitCost
                    : previousQuantity.multiply(previousCost).add(quantity.multiply(unitCost))
                            .divide(previousQuantity.add(quantity), Money.SCALE, RoundingMode.HALF_UP));
            itemRepository.save(item);

            subtotal = Money.add(subtotal, lineTotal);
        }

        grnLineRepository.saveAll(lines);
        if (!serials.isEmpty()) {
            itemSerialRepository.saveAll(serials);
        }

        grn.setSubtotal(subtotal);
        grn.setTaxAmount(Money.ZERO);
        grn.setTotal(subtotal);
        grn.markPosted();
        grn = grnRepository.save(grn);

        return GrnResponse.from(grn, lines, serials);
    }

    /**
     * Callers may send either a flat list of serial numbers or the richer per-unit
     * form. Sending both is a mistake worth failing on rather than guessing which wins.
     */
    private List<GrnUnitRequest> resolveUnits(Item item, GrnLineRequest lineRequest) {
        boolean hasUnits = lineRequest.units() != null && !lineRequest.units().isEmpty();
        boolean hasSerials = lineRequest.serialNumbers() != null && !lineRequest.serialNumbers().isEmpty();
        if (hasUnits && hasSerials) {
            throw ApiException.validation("Supply either serialNumbers or units, not both")
                    .with("itemId", item.getId());
        }
        if (hasUnits) {
            return lineRequest.units();
        }
        if (!hasSerials) {
            return List.of();
        }
        return lineRequest.serialNumbers().stream()
                .map(sn -> new GrnUnitRequest(sn, null, null, null, null, null))
                .toList();
    }

    private void validateSerialCount(Item item, BigDecimal quantity, List<GrnUnitRequest> units) {
        try {
            int expected = quantity.stripTrailingZeros().intValueExact();
            if (units.size() != expected) {
                throw ApiException.of(ErrorCode.SERIAL_COUNT_MISMATCH,
                                "Serial count must equal received quantity for serialised items")
                        .with("itemId", item.getId())
                        .with("quantity", quantity)
                        .with("serialCount", units.size());
            }
        } catch (ArithmeticException ex) {
            throw ApiException.validation("Serialised items require a whole-number quantity")
                    .with("itemId", item.getId())
                    .with("quantity", quantity);
        }
    }

    private List<ItemSerial> createSerials(GoodsReceivedNote grn,
                                           Item item,
                                           UUID outletId,
                                           BigDecimal unitCost,
                                           short warrantyMonths,
                                           List<GrnUnitRequest> units) {
        Set<String> seen = new HashSet<>();
        Set<String> seenImeis = new HashSet<>();
        List<ItemSerial> created = new ArrayList<>();
        Instant receivedAt = grn.getReceivedAt();
        LocalDate warrantyStart = receivedAt.atZone(ZoneOffset.UTC).toLocalDate();
        for (GrnUnitRequest unit : units) {
            String serialNumber = unit.serialNumber().trim();
            if (!seen.add(serialNumber.toLowerCase())) {
                throw ApiException.of(ErrorCode.SERIAL_ALREADY_EXISTS,
                                "Duplicate serial in GRN request")
                        .with("serialNumber", serialNumber);
            }
            if (itemSerialRepository.existsBySerialNumberIgnoreCase(serialNumber)) {
                throw ApiException.of(ErrorCode.SERIAL_ALREADY_EXISTS, "Serial already exists")
                        .with("serialNumber", serialNumber);
            }
            String imei1 = requireValidImei(unit.imei1(), serialNumber, seenImeis);
            String imei2 = requireValidImei(unit.imei2(), serialNumber, seenImeis);

            ItemSerial serial = new ItemSerial();
            serial.setItemId(item.getId());
            serial.setOutletId(outletId);
            serial.setSerialNumber(serialNumber);
            serial.setImei1(imei1);
            serial.setImei2(imei2);
            serial.setUnitCondition(unit.condition() == null ? UnitCondition.NEW : unit.condition());
            serial.setGrade(unit.grade());
            serial.setBatteryHealth(unit.batteryHealth());
            serial.setStatus(SerialStatus.IN_STOCK);
            serial.setGrnId(grn.getId());
            serial.setSupplierId(grn.getSupplierId());
            serial.setCostPrice(unitCost);
            serial.setReceivedAt(receivedAt);
            if (warrantyMonths > 0) {
                serial.setWarrantyStartsOn(warrantyStart);
                serial.setWarrantyEndsOn(warrantyStart.plusMonths(warrantyMonths));
            }
            created.add(serial);
        }
        return created;
    }

    /**
     * A mistyped IMEI produces a unit nobody can ever find by scanning the handset,
     * so the Luhn check digit is verified at intake rather than at sale time.
     */
    private String requireValidImei(String raw, String serialNumber, Set<String> seenInRequest) {
        String imei = Imei.normalize(raw);
        if (imei == null) {
            return null;
        }
        if (!Imei.isValid(imei)) {
            throw ApiException.validation("IMEI is not a valid 15-digit IMEI")
                    .with("serialNumber", serialNumber)
                    .with("imei", imei);
        }
        if (!seenInRequest.add(imei)) {
            throw ApiException.of(ErrorCode.SERIAL_ALREADY_EXISTS, "Duplicate IMEI in GRN request")
                    .with("imei", imei);
        }
        if (itemSerialRepository.existsByAnyImei(imei)) {
            throw ApiException.of(ErrorCode.SERIAL_ALREADY_EXISTS, "IMEI already exists in stock")
                    .with("imei", imei);
        }
        return imei;
    }

    private GrnResponse toResponse(GoodsReceivedNote grn, boolean includeSerials) {
        List<GrnLine> lines = grnLineRepository.findByGrnIdOrderByLineNumberAsc(grn.getId());
        List<ItemSerial> serials = includeSerials
                ? itemSerialRepository.findByGrnIdOrderBySerialNumberAsc(grn.getId())
                : List.of();
        return GrnResponse.from(grn, lines, serials);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
