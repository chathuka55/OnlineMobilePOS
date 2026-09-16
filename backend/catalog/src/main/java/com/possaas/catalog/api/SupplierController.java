package com.possaas.catalog.api;

import com.possaas.catalog.api.dto.CatalogDtos.SupplierRequest;
import com.possaas.catalog.api.dto.CatalogDtos.SupplierResponse;
import com.possaas.catalog.service.SupplierService;
import com.possaas.common.api.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('supplier.manage') or hasAuthority('grn.view') or hasAuthority('item.view')")
    public PageResponse<SupplierResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @PageableDefault(size = 50, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return PageResponse.of(supplierService.list(q, activeOnly, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('supplier.manage') or hasAuthority('grn.view') or hasAuthority('item.view')")
    public SupplierResponse get(@PathVariable UUID id) {
        return supplierService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('supplier.manage')")
    public SupplierResponse create(@Valid @RequestBody SupplierRequest request) {
        return supplierService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('supplier.manage')")
    public SupplierResponse update(@PathVariable UUID id,
                                   @Valid @RequestBody SupplierRequest request) {
        return supplierService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('supplier.manage')")
    public void delete(@PathVariable UUID id) {
        supplierService.softDelete(id);
    }
}
