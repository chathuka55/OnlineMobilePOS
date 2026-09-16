package com.possaas.catalog.service;

import com.possaas.catalog.api.dto.CatalogDtos.SupplierRequest;
import com.possaas.catalog.api.dto.CatalogDtos.SupplierResponse;
import com.possaas.catalog.domain.Supplier;
import com.possaas.catalog.repository.SupplierRepository;
import com.possaas.common.error.ApiException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;

    public SupplierService(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> list(String q, boolean activeOnly, Pageable pageable) {
        return supplierRepository.search(blankToNull(q), activeOnly, pageable)
                .map(SupplierResponse::from);
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(UUID id) {
        return SupplierResponse.from(require(id));
    }

    @Transactional(readOnly = true)
    public Supplier require(UUID id) {
        return supplierRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Supplier", id));
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        assertUniqueCode(request.code(), null);
        Supplier supplier = new Supplier();
        apply(supplier, request);
        return SupplierResponse.from(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierResponse update(UUID id, SupplierRequest request) {
        Supplier supplier = require(id);
        assertUniqueCode(request.code(), id);
        apply(supplier, request);
        return SupplierResponse.from(supplierRepository.save(supplier));
    }

    @Transactional
    public void softDelete(UUID id) {
        Supplier supplier = require(id);
        supplier.softDelete();
        supplierRepository.save(supplier);
    }

    private void apply(Supplier supplier, SupplierRequest request) {
        supplier.setCode(blankToNull(request.code()));
        supplier.setName(request.name().trim());
        supplier.setContactPerson(blankToNull(request.contactPerson()));
        supplier.setPhonePrimary(blankToNull(request.phonePrimary()));
        supplier.setPhoneSecondary(blankToNull(request.phoneSecondary()));
        supplier.setEmail(blankToNull(request.email()) == null
                ? null : request.email().trim().toLowerCase());
        supplier.setAddressLine1(blankToNull(request.addressLine1()));
        supplier.setAddressLine2(blankToNull(request.addressLine2()));
        supplier.setCity(blankToNull(request.city()));
        supplier.setTaxIdentifier(blankToNull(request.taxIdentifier()));
        if (request.paymentTermsDays() != null) {
            supplier.setPaymentTermsDays(request.paymentTermsDays());
        }
        supplier.setNotes(blankToNull(request.notes()));
        if (request.active() != null) {
            supplier.setActive(request.active());
        }
    }

    private void assertUniqueCode(String code, UUID excludingId) {
        if (code == null || code.isBlank()) {
            return;
        }
        supplierRepository.findByCodeIgnoreCaseAndDeletedAtIsNull(code.trim())
                .filter(existing -> excludingId == null || !existing.getId().equals(excludingId))
                .ifPresent(existing -> {
                    throw ApiException.conflict("Supplier code already in use")
                            .with("code", code.trim());
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
