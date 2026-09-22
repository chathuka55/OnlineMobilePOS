package com.possaas.catalog.api.dto;

import com.possaas.catalog.domain.Category;
import com.possaas.catalog.domain.GoodsReceivedNote;
import com.possaas.catalog.domain.GrnLine;
import com.possaas.catalog.domain.GrnStatus;
import com.possaas.catalog.domain.Item;
import com.possaas.catalog.domain.ItemBarcode;
import com.possaas.catalog.domain.ItemSerial;
import com.possaas.catalog.domain.SerialStatus;
import com.possaas.catalog.domain.SoldDocumentType;
import com.possaas.catalog.domain.Supplier;
import com.possaas.catalog.domain.SupplierReturn;
import com.possaas.catalog.domain.SupplierReturnLine;
import com.possaas.catalog.domain.SupplierReturnStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    // --- Category -----------------------------------------------------------

    public record CategoryRequest(
            @NotBlank @Size(max = 120) String name,
            UUID parentId,
            Integer displayOrder,
            Boolean active
    ) {
    }

    public record CategoryResponse(
            UUID id,
            String name,
            UUID parentId,
            int displayOrder,
            boolean active
    ) {
        public static CategoryResponse from(Category category) {
            return new CategoryResponse(
                    category.getId(),
                    category.getName(),
                    category.getParentId(),
                    category.getDisplayOrder(),
                    category.isActive());
        }
    }

    // --- Supplier -----------------------------------------------------------

    public record SupplierRequest(
            @Size(max = 30) String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 160) String contactPerson,
            @Size(max = 32) String phonePrimary,
            @Size(max = 32) String phoneSecondary,
            @Size(max = 255) String email,
            @Size(max = 180) String addressLine1,
            @Size(max = 180) String addressLine2,
            @Size(max = 90) String city,
            @Size(max = 60) String taxIdentifier,
            Short paymentTermsDays,
            String notes,
            Boolean active
    ) {
    }

    public record SupplierResponse(
            UUID id,
            String code,
            String name,
            String contactPerson,
            String phonePrimary,
            String phoneSecondary,
            String email,
            String addressLine1,
            String addressLine2,
            String city,
            String taxIdentifier,
            short paymentTermsDays,
            BigDecimal totalPurchased,
            BigDecimal outstandingPayable,
            String notes,
            boolean active
    ) {
        public static SupplierResponse from(Supplier supplier) {
            return new SupplierResponse(
                    supplier.getId(),
                    supplier.getCode(),
                    supplier.getName(),
                    supplier.getContactPerson(),
                    supplier.getPhonePrimary(),
                    supplier.getPhoneSecondary(),
                    supplier.getEmail(),
                    supplier.getAddressLine1(),
                    supplier.getAddressLine2(),
                    supplier.getCity(),
                    supplier.getTaxIdentifier(),
                    supplier.getPaymentTermsDays(),
                    supplier.getTotalPurchased(),
                    supplier.getOutstandingPayable(),
                    supplier.getNotes(),
                    supplier.isActive());
        }
    }

    // --- Item ---------------------------------------------------------------

    public record BarcodeRequest(
            @NotBlank @Size(max = 80) String barcode,
            Boolean primaryBarcode
    ) {
    }

    public record ItemRequest(
            @NotBlank @Size(max = 60) String sku,
            @NotBlank @Size(max = 200) String name,
            String description,
            UUID categoryId,
            UUID supplierId,
            UUID taxRateId,
            @Size(max = 20) String unitOfMeasure,
            BigDecimal costPrice,
            BigDecimal retailPrice,
            BigDecimal wholesalePrice,
            BigDecimal minSellingPrice,
            BigDecimal reorderLevel,
            BigDecimal reorderQuantity,
            Boolean trackInventory,
            Boolean hasSerialTracking,
            Boolean allowNegativeStock,
            Boolean oldStock,
            Short warrantyMonths,
            @Size(max = 60) String warrantyLabel,
            Boolean active,
            List<@Valid BarcodeRequest> barcodes
    ) {
    }

    public record BarcodeResponse(UUID id, String barcode, boolean primaryBarcode) {
        public static BarcodeResponse from(ItemBarcode barcode) {
            return new BarcodeResponse(barcode.getId(), barcode.getBarcode(), barcode.isPrimaryBarcode());
        }
    }

    public record ItemResponse(
            UUID id,
            String sku,
            String name,
            String description,
            UUID categoryId,
            UUID supplierId,
            UUID taxRateId,
            String unitOfMeasure,
            BigDecimal costPrice,
            BigDecimal retailPrice,
            BigDecimal wholesalePrice,
            BigDecimal minSellingPrice,
            BigDecimal quantityOnHand,
            BigDecimal quantityReserved,
            BigDecimal quantityDamaged,
            BigDecimal reorderLevel,
            BigDecimal reorderQuantity,
            boolean trackInventory,
            boolean hasSerialTracking,
            boolean allowNegativeStock,
            boolean oldStock,
            short warrantyMonths,
            String warrantyLabel,
            boolean active,
            List<BarcodeResponse> barcodes
    ) {
        public static ItemResponse from(Item item, List<ItemBarcode> barcodes) {
            return new ItemResponse(
                    item.getId(),
                    item.getSku(),
                    item.getName(),
                    item.getDescription(),
                    item.getCategoryId(),
                    item.getSupplierId(),
                    item.getTaxRateId(),
                    item.getUnitOfMeasure(),
                    item.getCostPrice(),
                    item.getRetailPrice(),
                    item.getWholesalePrice(),
                    item.getMinSellingPrice(),
                    item.getQuantityOnHand(),
                    item.getQuantityReserved(),
                    item.getQuantityDamaged(),
                    item.getReorderLevel(),
                    item.getReorderQuantity(),
                    item.isTrackInventory(),
                    item.isHasSerialTracking(),
                    item.isAllowNegativeStock(),
                    item.isOldStock(),
                    item.getWarrantyMonths(),
                    item.getWarrantyLabel(),
                    item.isActive(),
                    barcodes.stream().map(BarcodeResponse::from).toList());
        }
    }

    public record StockAdjustRequest(
            @NotNull BigDecimal delta,
            @Size(max = 240) String reason
    ) {
    }

    public record DamagedStockRequest(
            @NotNull @DecimalMin("0.001") BigDecimal quantity,
            @Size(max = 240) String reason
    ) {
    }

    public record RestoreDamagedRequest(
            @NotNull @DecimalMin("0.001") BigDecimal quantity,
            boolean toSellable,
            @Size(max = 240) String reason
    ) {
    }

    public record DamagedItemResponse(
            UUID itemId,
            String sku,
            String itemName,
            UUID supplierId,
            String supplierName,
            BigDecimal quantityDamaged,
            BigDecimal costPrice
    ) {
    }

    // --- Serial -------------------------------------------------------------

    public record SerialResponse(
            UUID id,
            UUID itemId,
            UUID outletId,
            String serialNumber,
            SerialStatus status,
            UUID grnId,
            UUID supplierId,
            BigDecimal costPrice,
            Instant receivedAt,
            Instant soldAt,
            SoldDocumentType soldDocumentType,
            UUID soldDocumentId,
            LocalDate warrantyStartsOn,
            LocalDate warrantyEndsOn,
            String notes
    ) {
        public static SerialResponse from(ItemSerial serial) {
            return new SerialResponse(
                    serial.getId(),
                    serial.getItemId(),
                    serial.getOutletId(),
                    serial.getSerialNumber(),
                    serial.getStatus(),
                    serial.getGrnId(),
                    serial.getSupplierId(),
                    serial.getCostPrice(),
                    serial.getReceivedAt(),
                    serial.getSoldAt(),
                    serial.getSoldDocumentType(),
                    serial.getSoldDocumentId(),
                    serial.getWarrantyStartsOn(),
                    serial.getWarrantyEndsOn(),
                    serial.getNotes());
        }
    }

    // --- GRN ----------------------------------------------------------------

    public record GrnLineRequest(
            @NotNull UUID itemId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @NotNull @DecimalMin("0") BigDecimal unitCost,
            BigDecimal retailPriceAtReceipt,
            Short warrantyMonths,
            List<@NotBlank String> serialNumbers
    ) {
    }

    public record GrnCreateRequest(
            UUID outletId,
            UUID supplierId,
            @Size(max = 60) String supplierInvoiceNo,
            String notes,
            Instant receivedAt,
            @NotEmpty List<@Valid GrnLineRequest> lines
    ) {
    }

    public record GrnLineResponse(
            UUID id,
            UUID itemId,
            short lineNumber,
            BigDecimal quantity,
            BigDecimal unitCost,
            BigDecimal lineTotal,
            BigDecimal retailPriceAtReceipt,
            short warrantyMonths
    ) {
        public static GrnLineResponse from(GrnLine line) {
            return new GrnLineResponse(
                    line.getId(),
                    line.getItemId(),
                    line.getLineNumber(),
                    line.getQuantity(),
                    line.getUnitCost(),
                    line.getLineTotal(),
                    line.getRetailPriceAtReceipt(),
                    line.getWarrantyMonths());
        }
    }

    public record GrnResponse(
            UUID id,
            UUID outletId,
            String grnNumber,
            UUID supplierId,
            String supplierInvoiceNo,
            GrnStatus status,
            Instant receivedAt,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal total,
            String notes,
            Instant postedAt,
            List<GrnLineResponse> lines,
            List<SerialResponse> serials
    ) {
        public static GrnResponse from(GoodsReceivedNote grn,
                                       List<GrnLine> lines,
                                       List<ItemSerial> serials) {
            return new GrnResponse(
                    grn.getId(),
                    grn.getOutletId(),
                    grn.getGrnNumber(),
                    grn.getSupplierId(),
                    grn.getSupplierInvoiceNo(),
                    grn.getStatus(),
                    grn.getReceivedAt(),
                    grn.getSubtotal(),
                    grn.getTaxAmount(),
                    grn.getTotal(),
                    grn.getNotes(),
                    grn.getPostedAt(),
                    lines.stream().map(GrnLineResponse::from).toList(),
                    serials.stream().map(SerialResponse::from).toList());
        }
    }

    // --- Supplier return ----------------------------------------------------

    public record SupplierReturnLineRequest(
            @NotNull UUID itemId,
            UUID itemSerialId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @NotNull @DecimalMin("0") BigDecimal unitCost,
            @Size(max = 240) String reason
    ) {
    }

    public record SupplierReturnCreateRequest(
            UUID outletId,
            UUID supplierId,
            @Size(max = 240) String reason,
            String notes,
            Instant returnedAt,
            @NotEmpty List<@Valid SupplierReturnLineRequest> lines
    ) {
    }

    public record SupplierReturnLineResponse(
            UUID id,
            UUID itemId,
            UUID itemSerialId,
            short lineNumber,
            BigDecimal quantity,
            BigDecimal unitCost,
            BigDecimal lineTotal,
            String reason,
            Boolean withinWarranty
    ) {
        public static SupplierReturnLineResponse from(SupplierReturnLine line) {
            return new SupplierReturnLineResponse(
                    line.getId(),
                    line.getItemId(),
                    line.getItemSerialId(),
                    line.getLineNumber(),
                    line.getQuantity(),
                    line.getUnitCost(),
                    line.getLineTotal(),
                    line.getReason(),
                    line.getWithinWarranty());
        }
    }

    public record SupplierReturnResponse(
            UUID id,
            UUID outletId,
            String returnNumber,
            UUID supplierId,
            SupplierReturnStatus status,
            Instant returnedAt,
            String reason,
            BigDecimal totalValue,
            String notes,
            Instant postedAt,
            List<SupplierReturnLineResponse> lines
    ) {
        public static SupplierReturnResponse from(SupplierReturn supplierReturn,
                                                  List<SupplierReturnLine> lines) {
            return new SupplierReturnResponse(
                    supplierReturn.getId(),
                    supplierReturn.getOutletId(),
                    supplierReturn.getReturnNumber(),
                    supplierReturn.getSupplierId(),
                    supplierReturn.getStatus(),
                    supplierReturn.getReturnedAt(),
                    supplierReturn.getReason(),
                    supplierReturn.getTotalValue(),
                    supplierReturn.getNotes(),
                    supplierReturn.getPostedAt(),
                    lines.stream().map(SupplierReturnLineResponse::from).toList());
        }
    }
}
