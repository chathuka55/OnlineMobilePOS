package com.possaas.catalog.service;

import com.possaas.catalog.api.dto.CatalogDtos.SerialEventResponse;
import com.possaas.catalog.api.dto.CatalogDtos.SerialLifecycleResponse;
import com.possaas.catalog.api.dto.CatalogDtos.SerialResponse;
import com.possaas.catalog.domain.GoodsReceivedNote;
import com.possaas.catalog.domain.Imei;
import com.possaas.catalog.domain.Item;
import com.possaas.catalog.domain.ItemSerial;
import com.possaas.catalog.domain.SerialStatus;
import com.possaas.catalog.domain.SoldDocumentType;
import com.possaas.catalog.domain.StockMovement;
import com.possaas.catalog.repository.GoodsReceivedNoteRepository;
import com.possaas.catalog.repository.ItemRepository;
import com.possaas.catalog.repository.ItemSerialRepository;
import com.possaas.catalog.repository.StockMovementRepository;
import com.possaas.common.error.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SerialService {

    private final ItemSerialRepository itemSerialRepository;
    private final StockLedgerService stockLedgerService;
    private final ItemRepository itemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final GoodsReceivedNoteRepository grnRepository;

    public SerialService(ItemSerialRepository itemSerialRepository,
                         StockLedgerService stockLedgerService,
                         ItemRepository itemRepository,
                         StockMovementRepository stockMovementRepository,
                         GoodsReceivedNoteRepository grnRepository) {
        this.itemSerialRepository = itemSerialRepository;
        this.stockLedgerService = stockLedgerService;
        this.itemRepository = itemRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.grnRepository = grnRepository;
    }

    @Transactional(readOnly = true)
    public Page<SerialResponse> search(String q, SerialStatus status, UUID itemId, Pageable pageable) {
        return itemSerialRepository.search(blankToNull(q), status, itemId, pageable)
                .map(SerialResponse::from);
    }

    @Transactional(readOnly = true)
    public SerialResponse get(UUID id) {
        return SerialResponse.from(require(id));
    }

    @Transactional(readOnly = true)
    public SerialResponse findByNumber(String serialNumber) {
        return SerialResponse.from(itemSerialRepository.findBySerialNumberIgnoreCase(serialNumber.trim())
                .orElseThrow(() -> ApiException.notFound("ItemSerial", serialNumber)));
    }

    /**
     * Counter lookup by whatever is printed on the device - IMEI or serial - plus
     * the unit's full history, so "who did we sell this to and is it still under
     * warranty?" is one scan rather than a hunt through invoices.
     */
    @Transactional(readOnly = true)
    public SerialLifecycleResponse lifecycle(String code) {
        String trimmed = code == null ? "" : code.trim();
        String normalizedImei = Imei.normalize(trimmed);
        ItemSerial unit = itemSerialRepository.findByImeiOrSerial(trimmed)
                .or(() -> normalizedImei == null
                        ? Optional.empty()
                        : itemSerialRepository.findByImeiOrSerial(normalizedImei))
                .orElseThrow(() -> ApiException.notFound("ItemSerial", trimmed));

        Item item = itemRepository.findById(unit.getItemId()).orElse(null);
        List<SerialEventResponse> timeline = buildTimeline(unit);

        boolean underWarranty = unit.getWarrantyEndsOn() != null
                && !unit.getWarrantyEndsOn().isBefore(LocalDate.now());

        return new SerialLifecycleResponse(
                SerialResponse.from(unit),
                item == null ? null : item.getName(),
                item == null ? null : item.getSku(),
                underWarranty,
                timeline);
    }

    /**
     * A unit's history lives in two places: its own lifecycle columns (received on a
     * GRN, sold against a document) and any stock movement that names it directly -
     * currently only supplier returns, because a SALE movement covers a whole line
     * and cannot point at one of several units.
     */
    private List<SerialEventResponse> buildTimeline(ItemSerial unit) {
        List<SerialEventResponse> events = new ArrayList<>();

        if (unit.getReceivedAt() != null) {
            String grnNumber = unit.getGrnId() == null ? null
                    : grnRepository.findById(unit.getGrnId())
                            .map(GoodsReceivedNote::getGrnNumber)
                            .orElse(null);
            events.add(new SerialEventResponse(
                    "RECEIVED",
                    BigDecimal.ONE,
                    unit.getCostPrice(),
                    unit.getGrnId() == null ? null : "GRN",
                    unit.getGrnId(),
                    grnNumber,
                    null,
                    unit.getReceivedAt()));
        }

        for (StockMovement movement : stockMovementRepository
                .findByItemSerialIdOrderByOccurredAtAsc(unit.getId())) {
            events.add(new SerialEventResponse(
                    movement.getMovementType().name(),
                    movement.getQuantityDelta(),
                    movement.getUnitCost(),
                    movement.getReferenceType(),
                    movement.getReferenceId(),
                    movement.getReferenceNumber(),
                    movement.getReason(),
                    movement.getOccurredAt()));
        }

        if (unit.getSoldAt() != null) {
            events.add(new SerialEventResponse(
                    "SOLD",
                    BigDecimal.ONE.negate(),
                    null,
                    unit.getSoldDocumentType() == null ? null : unit.getSoldDocumentType().name(),
                    unit.getSoldDocumentId(),
                    null,
                    null,
                    unit.getSoldAt()));
        }

        events.sort(Comparator.comparing(SerialEventResponse::occurredAt));
        return events;
    }

    @Transactional
    public List<SerialResponse> markSold(List<UUID> serialIds, String soldBillCode) {
        return stockLedgerService.markSerialsSold(serialIds, soldBillCode).stream()
                .map(SerialResponse::from)
                .toList();
    }

    @Transactional
    public List<SerialResponse> markSold(List<UUID> serialIds,
                                         SoldDocumentType documentType,
                                         UUID documentId) {
        return stockLedgerService.markSerialsSold(serialIds, documentType, documentId).stream()
                .map(SerialResponse::from)
                .toList();
    }

    @Transactional
    public List<SerialResponse> returnSerials(List<UUID> serialIds) {
        return stockLedgerService.returnSerials(serialIds).stream()
                .map(SerialResponse::from)
                .toList();
    }

    @Transactional
    public List<SerialResponse> returnByBill(UUID billId) {
        return stockLedgerService.returnSerialsByBill(billId).stream()
                .map(SerialResponse::from)
                .toList();
    }

    private ItemSerial require(UUID id) {
        return itemSerialRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("ItemSerial", id));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
