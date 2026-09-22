package com.possaas.catalog.service;

import com.possaas.catalog.domain.Item;
import com.possaas.catalog.domain.ItemSerial;
import com.possaas.catalog.domain.MovementType;
import com.possaas.catalog.domain.SerialStatus;
import com.possaas.catalog.domain.SoldDocumentType;
import com.possaas.catalog.domain.StockMovement;
import com.possaas.catalog.repository.ItemRepository;
import com.possaas.catalog.repository.ItemSerialRepository;
import com.possaas.catalog.repository.StockMovementRepository;
import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.money.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single writer for {@code stock_movements} and the denormalised
 * {@code items.quantity_on_hand} cache. Every inventory change in the system goes through
 * here so the ledger stays the source of truth.
 */
@Service
public class StockLedgerService {

    private final ItemRepository itemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ItemSerialRepository itemSerialRepository;

    public StockLedgerService(ItemRepository itemRepository,
                              StockMovementRepository stockMovementRepository,
                              ItemSerialRepository itemSerialRepository) {
        this.itemRepository = itemRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.itemSerialRepository = itemSerialRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement receiveGrn(UUID itemId,
                                    BigDecimal qty,
                                    BigDecimal unitCost,
                                    String refType,
                                    UUID refId) {
        return append(itemId, MovementType.GRN, Money.quantity(qty), Money.of(unitCost),
                refType, refId, null, null, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement sell(UUID itemId, BigDecimal qty, String refType, UUID refId) {
        return sell(itemId, qty, refType, refId, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement sell(UUID itemId,
                              BigDecimal qty,
                              String refType,
                              UUID refId,
                              String refNumber) {
        BigDecimal delta = Money.quantity(qty).negate();
        return append(itemId, MovementType.SALE, delta, null, refType, refId, refNumber, null, null);
    }

    /** Restores stock after a customer return (positive delta, {@link MovementType#SALE_RETURN}). */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement restore(UUID itemId, BigDecimal qty, String refType, UUID refId) {
        return restore(itemId, qty, refType, refId, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement restore(UUID itemId,
                                 BigDecimal qty,
                                 String refType,
                                 UUID refId,
                                 String refNumber) {
        return append(itemId, MovementType.SALE_RETURN, Money.quantity(qty), null,
                refType, refId, refNumber, null, null);
    }

    /**
     * Manual adjustment. Positive {@code delta} posts {@link MovementType#ADJUSTMENT_IN};
     * negative posts {@link MovementType#ADJUSTMENT_OUT}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement adjust(UUID itemId, BigDecimal delta, String reason) {
        BigDecimal quantity = Money.quantity(delta);
        if (quantity.signum() == 0) {
            throw ApiException.validation("Adjustment quantity cannot be zero");
        }
        MovementType type = quantity.signum() > 0
                ? MovementType.ADJUSTMENT_IN
                : MovementType.ADJUSTMENT_OUT;
        return append(itemId, type, quantity, null, null, null, null, reason, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement supplierReturn(UUID itemId,
                                        BigDecimal qty,
                                        BigDecimal unitCost,
                                        String refType,
                                        UUID refId,
                                        UUID itemSerialId) {
        BigDecimal delta = Money.quantity(qty).negate();
        return append(itemId, MovementType.SUPPLIER_RETURN, delta, Money.of(unitCost),
                refType, refId, null, null, itemSerialId);
    }

    /**
     * Marks serial units sold against a bill (or other document). Does not change
     * quantity_on_hand — the matching {@link #sell} call owns the ledger row.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ItemSerial> markSerialsSold(List<UUID> serialIds, String soldBillCode) {
        // Bill code is carried for callers that only have a human number; document id is
        // preferred when available via the overload below.
        return markSerialsSold(serialIds, SoldDocumentType.BILL, null, soldBillCode);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<ItemSerial> markSerialsSold(List<UUID> serialIds,
                                            SoldDocumentType documentType,
                                            UUID documentId) {
        return markSerialsSold(serialIds, documentType, documentId, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<ItemSerial> markSerialsSold(List<UUID> serialIds,
                                            SoldDocumentType documentType,
                                            UUID documentId,
                                            String soldBillCode) {
        if (serialIds == null || serialIds.isEmpty()) {
            return List.of();
        }
        List<ItemSerial> serials = loadSerials(serialIds);
        SoldDocumentType type = documentType == null ? SoldDocumentType.BILL : documentType;
        for (ItemSerial serial : serials) {
            if (!serial.isAvailable()) {
                throw ApiException.of(ErrorCode.SERIAL_NOT_AVAILABLE,
                                "Serial is not available for sale")
                        .with("serialId", serial.getId())
                        .with("serialNumber", serial.getSerialNumber())
                        .with("status", serial.getStatus().name());
            }
            if (documentId != null) {
                serial.markSold(type, documentId);
                if (soldBillCode != null && !soldBillCode.isBlank()) {
                    serial.setNotes(mergeNote(serial.getNotes(), "soldBillCode=" + soldBillCode.trim()));
                }
            } else if (soldBillCode != null && !soldBillCode.isBlank()) {
                // CHECK constraint requires type/id together, so bill-code-only sales leave
                // both null and record the human number on notes.
                serial.setStatus(SerialStatus.SOLD);
                serial.setSoldAt(java.time.Instant.now());
                serial.setSoldDocumentType(null);
                serial.setSoldDocumentId(null);
                serial.setNotes(mergeNote(serial.getNotes(), "soldBillCode=" + soldBillCode.trim()));
            } else {
                throw ApiException.validation("soldDocumentId or soldBillCode is required");
            }
        }
        return itemSerialRepository.saveAll(serials);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<ItemSerial> returnSerials(List<UUID> serialIds) {
        if (serialIds == null || serialIds.isEmpty()) {
            return List.of();
        }
        List<ItemSerial> serials = loadSerials(serialIds);
        for (ItemSerial serial : serials) {
            if (serial.getStatus() != SerialStatus.SOLD && serial.getStatus() != SerialStatus.RESERVED) {
                throw ApiException.of(ErrorCode.SERIAL_NOT_AVAILABLE,
                                "Serial cannot be returned in its current status")
                        .with("serialId", serial.getId())
                        .with("status", serial.getStatus().name());
            }
            serial.returnToStock();
        }
        return itemSerialRepository.saveAll(serials);
    }

    /** Returns every serial previously sold against the given document. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ItemSerial> returnSerials(SoldDocumentType documentType, UUID documentId) {
        List<ItemSerial> serials = itemSerialRepository
                .findBySoldDocumentTypeAndSoldDocumentId(documentType, documentId);
        if (serials.isEmpty()) {
            return List.of();
        }
        return returnSerials(serials.stream().map(ItemSerial::getId).toList());
    }

    /** Convenience for callers that only have a bill id. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ItemSerial> returnSerialsByBill(UUID billId) {
        return returnSerials(SoldDocumentType.BILL, billId);
    }

    /**
     * Moves stock out of sellable quantity_on_hand into the separate quantity_damaged
     * bucket, so damage is tracked (and reportable per supplier via items.supplier_id)
     * instead of just vanishing like a WRITE_OFF does.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement markDamaged(UUID itemId, BigDecimal qty, String reason) {
        BigDecimal quantity = Money.quantity(qty);
        if (quantity.signum() <= 0) {
            throw ApiException.validation("Damaged quantity must be positive");
        }

        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> ApiException.notFound("Item", itemId));
        if (!item.isTrackInventory()) {
            throw ApiException.validation("This item does not track inventory");
        }
        BigDecimal nextOnHand = Money.quantity(item.getQuantityOnHand().subtract(quantity));
        if (nextOnHand.signum() < 0 && !item.isAllowNegativeStock()) {
            throw ApiException.of(ErrorCode.INSUFFICIENT_STOCK, "Insufficient stock to mark as damaged")
                    .with("itemId", itemId)
                    .with("sku", item.getSku())
                    .with("requested", quantity)
                    .with("available", item.getQuantityOnHand());
        }

        item.applyQuantityDelta(quantity.negate());
        item.applyDamagedDelta(quantity);
        itemRepository.save(item);

        StockMovement movement = StockMovement.of(
                itemId, MovementType.DAMAGED, quantity.negate(), item.getQuantityOnHand(), null,
                null, null, null, reason, null);
        return stockMovementRepository.save(movement);
    }

    /** Reverses a damage entry - e.g. after a supplier replaces or credits the unit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement restoreDamaged(UUID itemId, BigDecimal qty, boolean toSellable, String reason) {
        BigDecimal quantity = Money.quantity(qty);
        if (quantity.signum() <= 0) {
            throw ApiException.validation("Quantity must be positive");
        }

        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> ApiException.notFound("Item", itemId));
        if (quantity.compareTo(item.getQuantityDamaged()) > 0) {
            throw ApiException.validation("Quantity exceeds damaged stock on hand")
                    .with("itemId", itemId)
                    .with("requested", quantity)
                    .with("damaged", item.getQuantityDamaged());
        }

        item.applyDamagedDelta(quantity.negate());
        BigDecimal ledgerDelta;
        if (toSellable) {
            item.applyQuantityDelta(quantity);
            ledgerDelta = quantity;
        } else {
            // Written off entirely (e.g. scrapped/disposed) - on-hand stays as-is.
            ledgerDelta = BigDecimal.ZERO;
        }
        itemRepository.save(item);

        if (ledgerDelta.signum() == 0) {
            return null;
        }
        StockMovement movement = StockMovement.of(
                itemId, MovementType.DAMAGED_RESTORED, ledgerDelta, item.getQuantityOnHand(), null,
                null, null, null, reason, null);
        return stockMovementRepository.save(movement);
    }

    private StockMovement append(UUID itemId,
                                 MovementType type,
                                 BigDecimal quantityDelta,
                                 BigDecimal unitCost,
                                 String referenceType,
                                 UUID referenceId,
                                 String referenceNumber,
                                 String reason,
                                 UUID itemSerialId) {
        if (quantityDelta == null || quantityDelta.signum() == 0) {
            throw ApiException.validation("Stock movement quantity cannot be zero");
        }
        if ((referenceType == null) != (referenceId == null)) {
            throw ApiException.validation("referenceType and referenceId must both be set or both null");
        }

        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> ApiException.notFound("Item", itemId));

        if (!item.isTrackInventory()) {
            // Service items and other non-stock SKUs never touch the ledger.
            return null;
        }

        BigDecimal nextBalance = Money.quantity(item.getQuantityOnHand().add(quantityDelta));
        if (quantityDelta.signum() < 0
                && !item.isAllowNegativeStock()
                && nextBalance.signum() < 0) {
            throw ApiException.of(ErrorCode.INSUFFICIENT_STOCK, "Insufficient stock")
                    .with("itemId", itemId)
                    .with("sku", item.getSku())
                    .with("requested", Money.quantity(quantityDelta.abs()))
                    .with("available", item.getQuantityOnHand());
        }

        item.applyQuantityDelta(quantityDelta);
        itemRepository.save(item);

        StockMovement movement = StockMovement.of(
                itemId, type, Money.quantity(quantityDelta), nextBalance, unitCost,
                referenceType, referenceId, referenceNumber, reason, itemSerialId);
        return stockMovementRepository.save(movement);
    }

    private List<ItemSerial> loadSerials(List<UUID> serialIds) {
        List<ItemSerial> found = itemSerialRepository.findByIdIn(serialIds);
        if (found.size() != serialIds.size()) {
            List<UUID> foundIds = found.stream().map(ItemSerial::getId).toList();
            List<UUID> missing = new ArrayList<>();
            for (UUID id : serialIds) {
                if (!foundIds.contains(id)) {
                    missing.add(id);
                }
            }
            throw ApiException.notFound("ItemSerial", missing.isEmpty() ? serialIds : missing);
        }
        return found;
    }

    private static String mergeNote(String existing, String addition) {
        if (existing == null || existing.isBlank()) {
            return addition;
        }
        return existing + "; " + addition;
    }
}
