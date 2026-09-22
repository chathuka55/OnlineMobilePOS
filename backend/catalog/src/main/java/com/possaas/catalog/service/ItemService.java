package com.possaas.catalog.service;

import com.possaas.catalog.api.dto.CatalogDtos.BarcodeRequest;
import com.possaas.catalog.api.dto.CatalogDtos.DamagedStockRequest;
import com.possaas.catalog.api.dto.CatalogDtos.ItemRequest;
import com.possaas.catalog.api.dto.CatalogDtos.ItemResponse;
import com.possaas.catalog.api.dto.CatalogDtos.RestoreDamagedRequest;
import com.possaas.catalog.api.dto.CatalogDtos.StockAdjustRequest;
import com.possaas.catalog.domain.Item;
import com.possaas.catalog.domain.ItemBarcode;
import com.possaas.catalog.repository.CategoryRepository;
import com.possaas.catalog.repository.ItemBarcodeRepository;
import com.possaas.catalog.repository.ItemRepository;
import com.possaas.catalog.repository.SupplierRepository;
import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.money.Money;
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
public class ItemService {

    private final ItemRepository itemRepository;
    private final ItemBarcodeRepository itemBarcodeRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final StockLedgerService stockLedgerService;

    public ItemService(ItemRepository itemRepository,
                       ItemBarcodeRepository itemBarcodeRepository,
                       CategoryRepository categoryRepository,
                       SupplierRepository supplierRepository,
                       StockLedgerService stockLedgerService) {
        this.itemRepository = itemRepository;
        this.itemBarcodeRepository = itemBarcodeRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.stockLedgerService = stockLedgerService;
    }

    @Transactional(readOnly = true)
    public Page<ItemResponse> search(String q, boolean activeOnly, Pageable pageable) {
        return itemRepository.search(blankToNull(q), activeOnly, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ItemResponse> lowStock(Pageable pageable) {
        return itemRepository.findLowStock(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ItemResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public Item require(UUID id) {
        return itemRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Item", id));
    }

    @Transactional(readOnly = true)
    public ItemResponse findByBarcode(String barcode) {
        ItemBarcode row = itemBarcodeRepository.findByBarcode(barcode.trim())
                .orElseThrow(() -> ApiException.notFound("Barcode", barcode));
        return toResponse(require(row.getItemId()));
    }

    @Transactional
    public ItemResponse create(ItemRequest request) {
        assertUniqueSku(request.sku(), null);
        validateRefs(request);
        Item item = new Item();
        apply(item, request);
        if (item.isHasSerialTracking()) {
            item.setTrackInventory(true);
        }
        item = itemRepository.save(item);
        if (request.barcodes() != null) {
            replaceBarcodes(item.getId(), request.barcodes());
        }
        return toResponse(item);
    }

    @Transactional
    public ItemResponse update(UUID id, ItemRequest request) {
        Item item = require(id);
        assertUniqueSku(request.sku(), id);
        validateRefs(request);
        apply(item, request);
        if (item.isHasSerialTracking()) {
            item.setTrackInventory(true);
        }
        item = itemRepository.save(item);
        if (request.barcodes() != null) {
            replaceBarcodes(item.getId(), request.barcodes());
        }
        return toResponse(item);
    }

    @Transactional
    public void softDelete(UUID id) {
        Item item = require(id);
        item.softDelete();
        itemRepository.save(item);
    }

    @Transactional
    public ItemResponse adjustStock(UUID id, StockAdjustRequest request) {
        require(id);
        stockLedgerService.adjust(id, request.delta(), request.reason());
        return toResponse(require(id));
    }

    @Transactional
    public ItemResponse markDamaged(UUID id, DamagedStockRequest request) {
        require(id);
        stockLedgerService.markDamaged(id, request.quantity(), request.reason());
        return toResponse(require(id));
    }

    @Transactional
    public ItemResponse restoreDamaged(UUID id, RestoreDamagedRequest request) {
        require(id);
        stockLedgerService.restoreDamaged(id, request.quantity(), request.toSellable(), request.reason());
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public List<com.possaas.catalog.api.dto.CatalogDtos.DamagedItemResponse> damagedItems(UUID supplierId) {
        List<Item> items = supplierId == null
                ? itemRepository.findAllDamaged()
                : itemRepository.findDamagedBySupplier(supplierId);
        java.util.Map<UUID, String> supplierNames = supplierRepository
                .findAllById(items.stream().map(Item::getSupplierId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.possaas.catalog.domain.Supplier::getId,
                        com.possaas.catalog.domain.Supplier::getName));
        return items.stream()
                .map(i -> new com.possaas.catalog.api.dto.CatalogDtos.DamagedItemResponse(
                        i.getId(),
                        i.getSku(),
                        i.getName(),
                        i.getSupplierId(),
                        i.getSupplierId() == null ? null : supplierNames.get(i.getSupplierId()),
                        i.getQuantityDamaged(),
                        i.getCostPrice()))
                .toList();
    }

    private ItemResponse toResponse(Item item) {
        List<ItemBarcode> barcodes = itemBarcodeRepository
                .findByItemIdOrderByPrimaryBarcodeDescCreatedAtAsc(item.getId());
        return ItemResponse.from(item, barcodes);
    }

    private void apply(Item item, ItemRequest request) {
        item.setSku(request.sku().trim());
        item.setName(request.name().trim());
        item.setDescription(blankToNull(request.description()));
        item.setCategoryId(request.categoryId());
        item.setSupplierId(request.supplierId());
        item.setTaxRateId(request.taxRateId());
        if (request.unitOfMeasure() != null && !request.unitOfMeasure().isBlank()) {
            item.setUnitOfMeasure(request.unitOfMeasure().trim());
        }
        if (request.costPrice() != null) {
            item.setCostPrice(Money.of(request.costPrice()));
        }
        if (request.retailPrice() != null) {
            item.setRetailPrice(Money.of(request.retailPrice()));
        }
        if (request.wholesalePrice() != null) {
            item.setWholesalePrice(Money.of(request.wholesalePrice()));
        }
        item.setMinSellingPrice(request.minSellingPrice() == null
                ? null : Money.of(request.minSellingPrice()));
        if (request.reorderLevel() != null) {
            item.setReorderLevel(Money.quantity(request.reorderLevel()));
        }
        if (request.reorderQuantity() != null) {
            item.setReorderQuantity(Money.quantity(request.reorderQuantity()));
        }
        if (request.trackInventory() != null) {
            item.setTrackInventory(request.trackInventory());
        }
        if (request.hasSerialTracking() != null) {
            item.setHasSerialTracking(request.hasSerialTracking());
        }
        if (request.allowNegativeStock() != null) {
            item.setAllowNegativeStock(request.allowNegativeStock());
        }
        if (request.oldStock() != null) {
            item.setOldStock(request.oldStock());
        }
        if (request.warrantyMonths() != null) {
            item.setWarrantyMonths(request.warrantyMonths());
        }
        item.setWarrantyLabel(blankToNull(request.warrantyLabel()));
        if (request.active() != null) {
            item.setActive(request.active());
        }
    }

    private void replaceBarcodes(UUID itemId, List<BarcodeRequest> barcodes) {
        itemBarcodeRepository.deleteByItemId(itemId);
        itemBarcodeRepository.flush();
        if (barcodes == null || barcodes.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        boolean primaryAssigned = false;
        List<ItemBarcode> rows = new ArrayList<>();
        for (BarcodeRequest request : barcodes) {
            String code = request.barcode().trim();
            if (!seen.add(code.toLowerCase())) {
                throw ApiException.of(ErrorCode.DUPLICATE_BARCODE, "Duplicate barcode in request")
                        .with("barcode", code);
            }
            if (itemBarcodeRepository.existsByBarcode(code)) {
                throw ApiException.of(ErrorCode.DUPLICATE_BARCODE, "Barcode already in use")
                        .with("barcode", code);
            }
            boolean primary = Boolean.TRUE.equals(request.primaryBarcode()) && !primaryAssigned;
            if (primary) {
                primaryAssigned = true;
            }
            rows.add(ItemBarcode.of(itemId, code, primary));
        }
        if (!primaryAssigned && !rows.isEmpty()) {
            rows.getFirst().setPrimaryBarcode(true);
        }
        itemBarcodeRepository.saveAll(rows);
    }

    private void validateRefs(ItemRequest request) {
        if (request.categoryId() != null
                && categoryRepository.findById(request.categoryId()).isEmpty()) {
            throw ApiException.notFound("Category", request.categoryId());
        }
        if (request.supplierId() != null
                && supplierRepository.findByIdAndDeletedAtIsNull(request.supplierId()).isEmpty()) {
            throw ApiException.notFound("Supplier", request.supplierId());
        }
    }

    private void assertUniqueSku(String sku, UUID excludingId) {
        itemRepository.findBySkuIgnoreCaseAndDeletedAtIsNull(sku.trim())
                .filter(existing -> excludingId == null || !existing.getId().equals(excludingId))
                .ifPresent(existing -> {
                    throw ApiException.of(ErrorCode.DUPLICATE_SKU, "SKU already in use")
                            .with("sku", sku.trim());
                });
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
