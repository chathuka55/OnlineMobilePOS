package com.possaas.catalog.api;

import com.possaas.catalog.api.dto.CatalogDtos.SupplierReturnCreateRequest;
import com.possaas.catalog.api.dto.CatalogDtos.SupplierReturnResponse;
import com.possaas.catalog.service.SupplierReturnService;
import com.possaas.common.api.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/supplier-returns")
public class SupplierReturnController {

    private final SupplierReturnService supplierReturnService;

    public SupplierReturnController(SupplierReturnService supplierReturnService) {
        this.supplierReturnService = supplierReturnService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('supplier_return.manage') or hasAuthority('grn.view')")
    public PageResponse<SupplierReturnResponse> list(
            @PageableDefault(size = 50, sort = "returnedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.of(supplierReturnService.list(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('supplier_return.manage') or hasAuthority('grn.view')")
    public SupplierReturnResponse get(@PathVariable UUID id) {
        return supplierReturnService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('supplier_return.manage')")
    public SupplierReturnResponse create(@Valid @RequestBody SupplierReturnCreateRequest request) {
        return supplierReturnService.createAndPost(request);
    }
}
