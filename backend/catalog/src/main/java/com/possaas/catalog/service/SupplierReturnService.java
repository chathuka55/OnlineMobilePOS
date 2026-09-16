package com.possaas.catalog.service;

import com.possaas.catalog.api.dto.CatalogDtos.SupplierReturnCreateRequest;
import com.possaas.catalog.api.dto.CatalogDtos.SupplierReturnLineRequest;
import com.possaas.catalog.api.dto.CatalogDtos.SupplierReturnResponse;
import com.possaas.catalog.domain.Item;
import com.possaas.catalog.domain.ItemSerial;
import com.possaas.catalog.domain.SerialStatus;
import com.possaas.catalog.domain.SupplierReturn;
import com.possaas.catalog.domain.SupplierReturnLine;
import com.possaas.catalog.repository.ItemRepository;
import com.possaas.catalog.repository.ItemSerialRepository;
import com.possaas.catalog.repository.SupplierReturnLineRepository;
import com.possaas.catalog.repository.SupplierReturnRepository;
import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.money.Money;
import com.possaas.tenancy.domain.DocumentType;
import com.possaas.tenancy.service.DocumentNumberService;
import com.possaas.tenancy.service.TenantService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierReturnService {

    private final SupplierReturnRepository supplierReturnRepository;
    private final SupplierReturnLineRepository supplierReturnLineRepository;
    private final ItemRepository itemRepository;
    private final ItemSerialRepository itemSerialRepository;
    private final StockLedgerService stockLedgerService;
    private final DocumentNumberService documentNumberService;
    private final TenantService tenantService;
    private final SupplierService supplierService;

    public SupplierReturnService(SupplierReturnRepository supplierReturnRepository,
                                 SupplierReturnLineRepository supplierReturnLineRepository,
                                 ItemRepository itemRepository,
                                 ItemSerialRepository itemSerialRepository,
                                 StockLedgerService stockLedgerService,
                                 DocumentNumberService documentNumberService,
                                 TenantService tenantService,
                                 SupplierService supplierService) {
        this.supplierReturnRepository = supplierReturnRepository;
        this.supplierReturnLineRepository = supplierReturnLineRepository;
        this.itemRepository = itemRepository;
        this.itemSerialRepository = itemSerialRepository;
        this.stockLedgerService = stockLedgerService;
        this.documentNumberService = documentNumberService;
        this.tenantService = tenantService;
        this.supplierService = supplierService;
    }

    @Transactional(readOnly = true)
    public Page<SupplierReturnResponse> list(Pageable pageable) {
        return supplierReturnRepository.findAllByOrderByReturnedAtDesc(pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SupplierReturnResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public SupplierReturnResponse createAndPost(SupplierReturnCreateRequest request) {
        if (request.supplierId() != null) {
            supplierService.require(request.supplierId());
        }
        UUID outletId = tenantService.resolveOutlet(request.outletId()).getId();

        SupplierReturn supplierReturn = new SupplierReturn();
        supplierReturn.setOutletId(outletId);
        supplierReturn.setReturnNumber(documentNumberService.next(DocumentType.SUPPLIER_RETURN));
        supplierReturn.setSupplierId(request.supplierId());
        supplierReturn.setReason(blankToNull(request.reason()));
        supplierReturn.setNotes(blankToNull(request.notes()));
        supplierReturn.setReturnedAt(
                request.returnedAt() == null ? Instant.now() : request.returnedAt());
        supplierReturn = supplierReturnRepository.save(supplierReturn);

        BigDecimal total = Money.ZERO;
        short lineNumber = 1;
        List<SupplierReturnLine> lines = new ArrayList<>();

        for (SupplierReturnLineRequest lineRequest : request.lines()) {
            Item item = itemRepository.findByIdAndDeletedAtIsNull(lineRequest.itemId())
                    .orElseThrow(() -> ApiException.notFound("Item", lineRequest.itemId()));

            BigDecimal quantity = Money.quantity(lineRequest.quantity());
            BigDecimal unitCost = Money.of(lineRequest.unitCost());
            BigDecimal lineTotal = Money.multiply(unitCost, quantity);

            if (item.isHasSerialTracking()) {
                if (lineRequest.itemSerialId() == null) {
                    throw ApiException.validation("Serialised items require itemSerialId on return lines")
                            .with("itemId", item.getId());
                }
                if (quantity.compareTo(BigDecimal.ONE) != 0) {
                    throw ApiException.validation("Serial return lines must have quantity 1")
                            .with("itemId", item.getId());
                }
                ItemSerial serial = itemSerialRepository.findById(lineRequest.itemSerialId())
                        .orElseThrow(() -> ApiException.notFound("ItemSerial", lineRequest.itemSerialId()));
                if (!serial.getItemId().equals(item.getId())) {
                    throw ApiException.validation("Serial does not belong to the return line item");
                }
                if (serial.getStatus() != SerialStatus.IN_STOCK
                        && serial.getStatus() != SerialStatus.DEFECTIVE
                        && serial.getStatus() != SerialStatus.RETURNED) {
                    throw ApiException.of(ErrorCode.SERIAL_NOT_AVAILABLE,
                                    "Serial is not available to return to supplier")
                            .with("serialId", serial.getId())
                            .with("status", serial.getStatus().name());
                }
                serial.markDefective();
                itemSerialRepository.save(serial);
            }

            SupplierReturnLine line = new SupplierReturnLine();
            line.setSupplierReturnId(supplierReturn.getId());
            line.setItemId(item.getId());
            line.setItemSerialId(lineRequest.itemSerialId());
            line.setLineNumber(lineNumber++);
            line.setQuantity(quantity);
            line.setUnitCost(unitCost);
            line.setLineTotal(lineTotal);
            line.setReason(blankToNull(lineRequest.reason()));
            if (lineRequest.itemSerialId() != null) {
                line.setWithinWarranty(itemSerialRepository.findById(lineRequest.itemSerialId())
                        .map(ItemSerial::getWarrantyEndsOn)
                        .map(end -> !end.isBefore(java.time.LocalDate.now()))
                        .orElse(null));
            }
            lines.add(line);

            stockLedgerService.supplierReturn(
                    item.getId(),
                    quantity,
                    unitCost,
                    "SUPPLIER_RETURN",
                    supplierReturn.getId(),
                    lineRequest.itemSerialId());

            total = Money.add(total, lineTotal);
        }

        supplierReturnLineRepository.saveAll(lines);
        supplierReturn.setTotalValue(total);
        supplierReturn.markPosted();
        supplierReturn = supplierReturnRepository.save(supplierReturn);
        return SupplierReturnResponse.from(supplierReturn, lines);
    }

    private SupplierReturn require(UUID id) {
        return supplierReturnRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("SupplierReturn", id));
    }

    private SupplierReturnResponse toResponse(SupplierReturn supplierReturn) {
        List<SupplierReturnLine> lines = supplierReturnLineRepository
                .findBySupplierReturnIdOrderByLineNumberAsc(supplierReturn.getId());
        return SupplierReturnResponse.from(supplierReturn, lines);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
