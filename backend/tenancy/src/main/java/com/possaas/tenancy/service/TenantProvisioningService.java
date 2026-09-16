package com.possaas.tenancy.service;

import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.tenant.TenantContext;
import com.possaas.tenancy.domain.LookupValue;
import com.possaas.tenancy.domain.Outlet;
import com.possaas.tenancy.domain.TaxRate;
import com.possaas.tenancy.domain.Tenant;
import com.possaas.tenancy.repository.LookupValueRepository;
import com.possaas.tenancy.repository.OutletRepository;
import com.possaas.tenancy.repository.TaxRateRepository;
import com.possaas.tenancy.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a new tenant and everything it needs to make its first sale.
 *
 * <p>Provisioning runs before any tenant scope exists, so the newly-created rows have to
 * be written under an explicit {@link TenantContext} scope. Without it the Row-Level
 * Security {@code WITH CHECK} clause would reject the inserts.
 */
@Service
public class TenantProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioningService.class);

    /** Sri Lanka's headline VAT rate, seeded inactive so a shop opts in deliberately. */
    private static final BigDecimal DEFAULT_VAT_PERCENT = new BigDecimal("18.0000");

    private final TenantRepository tenantRepository;
    private final OutletRepository outletRepository;
    private final TaxRateRepository taxRateRepository;
    private final LookupValueRepository lookupValueRepository;
    private final EntityManager entityManager;

    public TenantProvisioningService(TenantRepository tenantRepository,
                                     OutletRepository outletRepository,
                                     TaxRateRepository taxRateRepository,
                                     LookupValueRepository lookupValueRepository,
                                     EntityManager entityManager) {
        this.tenantRepository = tenantRepository;
        this.outletRepository = outletRepository;
        this.taxRateRepository = taxRateRepository;
        this.lookupValueRepository = lookupValueRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public Tenant provision(String businessName, String contactEmail, String requestedSlug) {
        String slug = uniqueSlug(requestedSlug != null ? requestedSlug : businessName);

        Tenant tenant = Tenant.create(slug, businessName.trim(), contactEmail.trim().toLowerCase(Locale.ROOT));
        tenant.setOnboardedAt(Instant.now());
        tenantRepository.saveAndFlush(tenant);

        // Everything below is tenant-scoped, so bind the scope explicitly.
        TenantContext.runAs(
                TenantContext.Scope.forTenant(tenant.getId(), slug, null, null, contactEmail),
                () -> seedDefaults(tenant));

        log.info("Provisioned tenant {} ({})", businessName, slug);
        return tenant;
    }

    private void seedDefaults(Tenant tenant) {
        Outlet outlet = Outlet.createDefault(tenant.getBusinessName());
        outlet.setTenantId(tenant.getId());
        outlet.setEmail(tenant.getContactEmail());
        outlet.setPhonePrimary(tenant.getContactPhone());
        outletRepository.save(outlet);

        TaxRate vat = new TaxRate();
        vat.setTenantId(tenant.getId());
        vat.setCode("VAT");
        vat.setName("VAT");
        vat.setRatePercent(DEFAULT_VAT_PERCENT);
        vat.setInclusive(true);
        vat.setDefaultRate(false);
        vat.setActive(false);
        taxRateRepository.save(vat);

        TaxRate exempt = new TaxRate();
        exempt.setTenantId(tenant.getId());
        exempt.setCode("NONE");
        exempt.setName("No tax");
        exempt.setRatePercent(BigDecimal.ZERO);
        exempt.setDefaultRate(true);
        exempt.setActive(true);
        taxRateRepository.save(exempt);

        seedLookups(tenant, LookupValue.LookupType.REPAIR_TYPE, List.of(
                "Screen Replacement", "Battery Replacement", "Charging Port Repair",
                "Software / Firmware", "Water Damage Treatment", "Camera Replacement",
                "Speaker / Microphone", "Data Recovery", "Unlocking", "Diagnostics Only"));

        seedLookups(tenant, LookupValue.LookupType.DEVICE_CONDITION, List.of(
                "New Condition", "Minor Scratches", "Screen Cracked", "Back Glass Cracked",
                "Frame Dented", "Water Damaged", "Not Powering On", "Battery Swollen",
                "Previously Repaired"));

        seedLookups(tenant, LookupValue.LookupType.BORROWED_ITEM, List.of(
                "SIM Card", "Memory Card", "Charger", "Cable", "Back Cover",
                "Screen Protector", "Case", "Earphones"));

        seedLookups(tenant, LookupValue.LookupType.REFUND_REASON, List.of(
                "Faulty Product", "Wrong Item Supplied", "Customer Changed Mind",
                "Duplicate Billing", "Price Correction"));

        seedLookups(tenant, LookupValue.LookupType.RETURN_REASON, List.of(
                "Dead On Arrival", "Damaged In Transit", "Wrong Specification",
                "Excess Stock", "Warranty Claim"));

        seedLookups(tenant, LookupValue.LookupType.BANK, List.of(
                "Bank of Ceylon", "People's Bank", "Commercial Bank", "Hatton National Bank",
                "Sampath Bank", "Seylan Bank", "NDB", "DFCC Bank", "NSB"));

        // Hibernate defers these inserts by default; flushing here, while the
        // caller's TenantContext.runAs scope is still bound, guarantees they run
        // under the correct RLS scope instead of racing the scope being restored
        // to unscoped once this method returns.
        entityManager.flush();
    }

    private void seedLookups(Tenant tenant, LookupValue.LookupType type, List<String> values) {
        int order = 0;
        for (String value : values) {
            LookupValue lookup = LookupValue.of(type, value, order++);
            lookup.setTenantId(tenant.getId());
            lookupValueRepository.save(lookup);
        }
    }

    /**
     * Derives a URL-safe slug and disambiguates collisions with a numeric suffix, so two
     * shops called "Mobile Zone" both get an account.
     */
    private String uniqueSlug(String source) {
        String base = slugify(source);
        if (base.length() < 3) {
            base = "shop-" + base;
        }
        if (!tenantRepository.existsBySlugIgnoreCase(base)) {
            return base;
        }
        for (int suffix = 2; suffix <= 200; suffix++) {
            String candidate = base + "-" + suffix;
            if (!tenantRepository.existsBySlugIgnoreCase(candidate)) {
                return candidate;
            }
        }
        throw new ApiException(ErrorCode.CONFLICT,
                "Could not derive an available workspace address from '" + source + "'");
    }

    private static String slugify(String source) {
        String normalised = Normalizer.normalize(source, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        return normalised.length() > 40 ? normalised.substring(0, 40).replaceAll("-+$", "") : normalised;
    }
}
