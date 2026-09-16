package com.possaas.catalog.api;

import com.possaas.catalog.api.dto.CatalogDtos.SerialResponse;
import com.possaas.catalog.domain.SerialStatus;
import com.possaas.catalog.domain.SoldDocumentType;
import com.possaas.catalog.service.SerialService;
import com.possaas.common.api.PageResponse;
import com.possaas.common.error.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/serials")
public class SerialController {

    private final SerialService serialService;

    public SerialController(SerialService serialService) {
        this.serialService = serialService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('serial.view')")
    public PageResponse<SerialResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) SerialStatus status,
            @RequestParam(required = false) UUID itemId,
            @PageableDefault(size = 50, sort = "serialNumber", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return PageResponse.of(serialService.search(q, status, itemId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('serial.view')")
    public SerialResponse get(@PathVariable UUID id) {
        return serialService.get(id);
    }

    @GetMapping("/by-number/{serialNumber}")
    @PreAuthorize("hasAuthority('serial.view')")
    public SerialResponse byNumber(@PathVariable String serialNumber) {
        return serialService.findByNumber(serialNumber);
    }

    @PostMapping("/mark-sold")
    @PreAuthorize("hasAuthority('serial.manage') or hasAuthority('sale.create')")
    public List<SerialResponse> markSold(@Valid @RequestBody MarkSoldRequest request) {
        if (request.documentId() != null) {
            return serialService.markSold(
                    request.serialIds(),
                    request.documentType() == null ? SoldDocumentType.BILL : request.documentType(),
                    request.documentId());
        }
        return serialService.markSold(request.serialIds(), request.soldBillCode());
    }

    @PostMapping("/return")
    @PreAuthorize("hasAuthority('serial.manage') or hasAuthority('sale.refund')")
    public List<SerialResponse> returnSerials(@RequestBody ReturnSerialsRequest request) {
        if (request.billId() != null) {
            return serialService.returnByBill(request.billId());
        }
        if (request.serialIds() == null || request.serialIds().isEmpty()) {
            throw ApiException.validation("serialIds or billId is required");
        }
        return serialService.returnSerials(request.serialIds());
    }

    public record MarkSoldRequest(
            @NotEmpty List<UUID> serialIds,
            String soldBillCode,
            SoldDocumentType documentType,
            UUID documentId
    ) {
    }

    public record ReturnSerialsRequest(
            List<UUID> serialIds,
            UUID billId
    ) {
    }
}
