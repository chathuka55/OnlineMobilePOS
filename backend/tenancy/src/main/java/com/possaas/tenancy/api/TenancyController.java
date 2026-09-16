package com.possaas.tenancy.api;

import com.possaas.tenancy.domain.LookupValue;
import com.possaas.tenancy.domain.Outlet;
import com.possaas.tenancy.domain.TaxRate;
import com.possaas.tenancy.domain.Tenant;
import com.possaas.tenancy.repository.LookupValueRepository;
import com.possaas.tenancy.repository.OutletRepository;
import com.possaas.tenancy.repository.TaxRateRepository;
import com.possaas.tenancy.service.SettingsService;
import com.possaas.tenancy.service.TenantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shop profile, outlets, tax rates, lookups and settings — everything the desktop
 * ProfilePanel / SettingsPanel used to own.
 */
@RestController
@RequestMapping("/api/v1")
public class TenancyController {

    private final TenantService tenantService;
    private final OutletRepository outletRepository;
    private final TaxRateRepository taxRateRepository;
    private final LookupValueRepository lookupValueRepository;
    private final SettingsService settingsService;

    public TenancyController(TenantService tenantService,
                             OutletRepository outletRepository,
                             TaxRateRepository taxRateRepository,
                             LookupValueRepository lookupValueRepository,
                             SettingsService settingsService) {
        this.tenantService = tenantService;
        this.outletRepository = outletRepository;
        this.taxRateRepository = taxRateRepository;
        this.lookupValueRepository = lookupValueRepository;
        this.settingsService = settingsService;
    }

    @GetMapping("/tenant")
    public TenantResponse currentTenant() {
        return TenantResponse.from(tenantService.currentTenant());
    }

    @PutMapping("/tenant")
    @PreAuthorize("hasAuthority('settings:write')")
    public TenantResponse updateTenant(@Valid @RequestBody UpdateTenantRequest request) {
        Tenant tenant = tenantService.currentTenant();
        tenant.setBusinessName(request.businessName().trim());
        if (request.legalName() != null) {
            tenant.setLegalName(request.legalName().trim());
        }
        if (request.taxIdentifier() != null) {
            tenant.setTaxIdentifier(request.taxIdentifier().trim());
        }
        if (request.contactPhone() != null) {
            tenant.setContactPhone(request.contactPhone().trim());
        }
        if (request.timeZone() != null) {
            tenant.setTimeZone(request.timeZone().trim());
        }
        return TenantResponse.from(tenantService.save(tenant));
    }

    @GetMapping("/outlets")
    public List<OutletResponse> listOutlets() {
        return outletRepository.findAll().stream().map(OutletResponse::from).toList();
    }

    @GetMapping("/outlets/{id}")
    public OutletResponse getOutlet(@PathVariable UUID id) {
        return OutletResponse.from(tenantService.resolveOutlet(id));
    }

    @PutMapping("/outlets/{id}")
    @PreAuthorize("hasAuthority('settings:write')")
    public OutletResponse updateOutlet(@PathVariable UUID id,
                                       @Valid @RequestBody UpdateOutletRequest request) {
        Outlet outlet = tenantService.resolveOutlet(id);
        outlet.setName(request.name().trim());
        outlet.setAddressLine1(request.addressLine1());
        outlet.setAddressLine2(request.addressLine2());
        outlet.setCity(request.city());
        outlet.setPhonePrimary(request.phonePrimary());
        outlet.setPhoneSecondary(request.phoneSecondary());
        outlet.setEmail(request.email());
        outlet.setWebsite(request.website());
        outlet.setReceiptFooter(request.receiptFooter());
        return OutletResponse.from(outletRepository.save(outlet));
    }

    @GetMapping("/tax-rates")
    public List<TaxRate> taxRates() {
        return taxRateRepository.findAll();
    }

    @GetMapping("/lookups/{type}")
    public List<LookupValue> lookups(@PathVariable LookupValue.LookupType type) {
        return lookupValueRepository
                .findByLookupTypeAndActiveIsTrueOrderByDisplayOrderAscValueAsc(type);
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('settings:read')")
    public Map<String, String> settings() {
        return settingsService.effectiveSettings();
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('settings:write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateSettings(@RequestBody Map<String, String> values) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key != null && value != null) {
                sanitized.put(key, value);
            }
        });
        settingsService.putAll(sanitized);
    }

    public record TenantResponse(
            UUID id,
            String slug,
            String businessName,
            String legalName,
            String taxIdentifier,
            String status,
            String defaultCurrency,
            String timeZone,
            String contactEmail,
            String contactPhone
    ) {
        static TenantResponse from(Tenant tenant) {
            return new TenantResponse(
                    tenant.getId(),
                    tenant.getSlug(),
                    tenant.getBusinessName(),
                    tenant.getLegalName(),
                    tenant.getTaxIdentifier(),
                    tenant.getStatus().name(),
                    tenant.getDefaultCurrency(),
                    tenant.getTimeZone(),
                    tenant.getContactEmail(),
                    tenant.getContactPhone());
        }
    }

    public record UpdateTenantRequest(
            @NotBlank @Size(max = 160) String businessName,
            @Size(max = 160) String legalName,
            @Size(max = 60) String taxIdentifier,
            @Size(max = 32) String contactPhone,
            @Size(max = 60) String timeZone
    ) {
    }

    public record OutletResponse(
            UUID id,
            String code,
            String name,
            boolean defaultOutlet,
            String addressLine1,
            String addressLine2,
            String city,
            String phonePrimary,
            String phoneSecondary,
            String email,
            String website,
            String receiptFooter
    ) {
        static OutletResponse from(Outlet outlet) {
            return new OutletResponse(
                    outlet.getId(),
                    outlet.getCode(),
                    outlet.getName(),
                    outlet.isDefaultOutlet(),
                    outlet.getAddressLine1(),
                    outlet.getAddressLine2(),
                    outlet.getCity(),
                    outlet.getPhonePrimary(),
                    outlet.getPhoneSecondary(),
                    outlet.getEmail(),
                    outlet.getWebsite(),
                    outlet.getReceiptFooter());
        }
    }

    public record UpdateOutletRequest(
            @NotBlank @Size(max = 160) String name,
            String addressLine1,
            String addressLine2,
            String city,
            String phonePrimary,
            String phoneSecondary,
            String email,
            String website,
            String receiptFooter
    ) {
    }
}
