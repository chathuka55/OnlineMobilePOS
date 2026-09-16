package com.possaas.catalog.service;

import com.possaas.catalog.api.dto.CatalogDtos.SerialResponse;
import com.possaas.catalog.domain.ItemSerial;
import com.possaas.catalog.domain.SerialStatus;
import com.possaas.catalog.domain.SoldDocumentType;
import com.possaas.catalog.repository.ItemSerialRepository;
import com.possaas.common.error.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SerialService {

    private final ItemSerialRepository itemSerialRepository;
    private final StockLedgerService stockLedgerService;

    public SerialService(ItemSerialRepository itemSerialRepository,
                         StockLedgerService stockLedgerService) {
        this.itemSerialRepository = itemSerialRepository;
        this.stockLedgerService = stockLedgerService;
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
