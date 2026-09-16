package com.possaas.common.jpa;

import com.possaas.common.id.Uuid;
import com.possaas.common.tenant.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Transient;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Tenant-scoped row without optimistic locking. Used for child documents and ledger
 * lines that the schema intentionally leaves unversioned.
 */
@MappedSuperclass
public abstract class TenantOwnedEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = Uuid.v7();

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Transient
    private boolean persisted;

    @Override
    public UUID getId() {
        return id;
    }

    protected void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    @Override
    public boolean isNew() {
        return !persisted;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        this.persisted = true;
    }

    @PrePersist
    void applyTenantFromContext() {
        if (tenantId == null) {
            tenantId = TenantContext.requireTenantId();
        }
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TenantOwnedEntity that)) {
            return false;
        }
        return getClass().equals(org.hibernate.Hibernate.getClass(other))
                && Objects.equals(id, that.id);
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(id);
    }
}
