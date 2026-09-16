package com.possaas.common.jpa;

import com.possaas.common.tenant.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.util.UUID;

/**
 * Base class for tenant-scoped rows.
 *
 * <p>{@code tenant_id} is stamped automatically from {@link TenantContext} on insert, so
 * no service ever has to remember it. Reads are constrained by the PostgreSQL
 * Row-Level Security policies rather than by a Hibernate filter: enforcing isolation in
 * the database means a missing {@code WHERE} clause yields an empty result instead of
 * another shop's data.
 */
@MappedSuperclass
public abstract class TenantEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    public UUID getTenantId() {
        return tenantId;
    }

    /**
     * Only used when creating rows outside a request scope, such as tenant provisioning
     * and the demo-data seeder.
     */
    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    @PrePersist
    void applyTenantFromContext() {
        if (tenantId == null) {
            // requireTenantId() rather than a silent null: an unstamped row would be
            // rejected by the RLS WITH CHECK clause with a far less obvious error.
            tenantId = TenantContext.requireTenantId();
        }
    }
}
