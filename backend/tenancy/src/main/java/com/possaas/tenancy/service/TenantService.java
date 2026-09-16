package com.possaas.tenancy.service;

import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.tenant.TenantContext;
import com.possaas.tenancy.domain.Outlet;
import com.possaas.tenancy.domain.Tenant;
import com.possaas.tenancy.repository.OutletRepository;
import com.possaas.tenancy.repository.TenantRepository;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final OutletRepository outletRepository;

    public TenantService(TenantRepository tenantRepository, OutletRepository outletRepository) {
        this.tenantRepository = tenantRepository;
        this.outletRepository = outletRepository;
    }

    /**
     * Resolves the workspace address supplied at login. Cached because it is hit on every
     * token refresh.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "tenantBySlug", key = "#slug.toLowerCase()")
    public Tenant requireBySlug(String slug) {
        return tenantRepository.findBySlugIgnoreCaseAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ApiException(ErrorCode.TENANT_NOT_FOUND,
                        "No workspace found at '" + slug + "'"));
    }

    @Transactional(readOnly = true)
    public Tenant requireById(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .filter(tenant -> tenant.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Tenant", tenantId));
    }

    @Transactional(readOnly = true)
    public Tenant currentTenant() {
        return requireById(TenantContext.requireTenantId());
    }

    /** The outlet the terminal is bound to, falling back to the tenant's default. */
    @Transactional(readOnly = true)
    public Outlet resolveOutlet(UUID outletId) {
        if (outletId != null) {
            return outletRepository.findById(outletId)
                    .orElseThrow(() -> ApiException.notFound("Outlet", outletId));
        }
        return outletRepository.findByDefaultOutletIsTrue()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "This workspace has no default outlet configured"));
    }

    /**
     * Rejects writes from a tenant whose subscription has lapsed. Called by the
     * read-only guard rather than scattered through each service.
     */
    public void assertWritable(Tenant tenant) {
        if (!tenant.canWrite()) {
            throw new ApiException(ErrorCode.SUBSCRIPTION_READ_ONLY,
                    "This workspace is read-only until the subscription is brought up to date")
                    .with("tenantStatus", tenant.getStatus().name());
        }
    }

    @Transactional
    @CacheEvict(cacheNames = "tenantBySlug", key = "#tenant.slug.toLowerCase()")
    public Tenant save(Tenant tenant) {
        return tenantRepository.save(tenant);
    }
}
