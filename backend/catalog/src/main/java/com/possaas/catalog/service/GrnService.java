package com.possaas.catalog.service;

import com.possaas.catalog.api.dto.CatalogDtos.GrnCreateRequest;
import com.possaas.catalog.api.dto.CatalogDtos.GrnLineRequest;
import com.possaas.catalog.api.dto.CatalogDtos.GrnResponse;
import com.possaas.catalog.domain.GoodsReceivedNote;
import com.possaas.catalog.domain.GrnLine;
import com.possaas.catalog.domain.Item;
import com.possaas.catalog.domain.ItemSerial;
import com.possaas.catalog.domain.SerialStatus;
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

            List<String> serialNumbers = lineRequest.serialNumbers() == null
                    ? List.of()
                    : lineRequest.serialNumbers();
            if (item.isHasSerialTracking()) {
                validateSerialCount(item, quantity, serialNumbers);
                serials.addAll(createSerials(grn, item, outletId, unitCost, warrantyMonths, serialNumbers));
            } else if (!serialNumbers.isEmpty()) {
                throw ApiException.validation("Serial numbers supplied for a non-serialised item")
                        .with("itemId", item.getId());
            }

            stockLedgerService.receiveGrn(
                    item.getId(), quantity, unitCost, "GRN", grn.getId());

            // Keep item cost in step with the latest receipt when receiving stock.
            item.setCostPrice(unitCost);
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

    private void validateSerialCount(Item item, BigDecimal quantity, List<String> serialNumbers) {
        try {
            int expected = quantity.stripTrailingZeros().intValueExact();
            if (serialNumbers.size() != expected) {
                throw ApiException.of(ErrorCode.SERIAL_COUNT_MISMATCH,
                                "Serial count must equal received quantity for serialised items")
                        .with("itemId", item.getId())
                        .with("quantity", quantity)
                        .with("serialCount", serialNumbers.size());
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
                                           List<String> serialNumbers) {
        Set<String> seen = new HashSet<>();
        List<ItemSerial> created = new ArrayList<>();
        Instant receivedAt = grn.getReceivedAt();
        LocalDate warrantyStart = receivedAt.atZone(ZoneOffset.UTC).toLocalDate();
        for (String raw : serialNumbers) {
            String serialNumber = raw.trim();
            if (!seen.add(serialNumber.toLowerCase())) {
                throw ApiException.of(ErrorCode.SERIAL_ALREADY_EXISTS,
                                "Duplicate serial in GRN request")
                        .with("serialNumber", serialNumber);
            }
            if (itemSerialRepository.existsBySerialNumberIgnoreCase(serialNumber)) {
                throw ApiException.of(ErrorCode.SERIAL_ALREADY_EXISTS, "Serial already exists")
                        .with("serialNumber", serialNumber);
            }
            ItemSerial serial = new ItemSerial();
            serial.setItemId(item.getId());
            serial.setOutletId(outletId);
            serial.setSerialNumber(serialNumber);
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
