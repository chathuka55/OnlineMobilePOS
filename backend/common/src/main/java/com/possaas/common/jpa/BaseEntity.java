package com.possaas.common.jpa;

import com.possaas.common.id.Uuid;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Identity, timestamps and optimistic locking for every persisted entity.
 *
 * <p>Ids are assigned in Java, not by the database, so an entity is fully identified the
 * moment it is constructed. That is what lets a POS terminal build a whole bill graph
 * before it ever reaches the server.
 *
 * <p>Because the id is pre-assigned, Spring Data can no longer infer "new" from a null
 * id, so {@link Persistable} is implemented explicitly. Without it every {@code save()}
 * would issue a needless {@code SELECT} before the {@code INSERT}.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = Uuid.v7();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Transient
    private boolean persisted;

    @Override
    public UUID getId() {
        return id;
    }

    protected void setId(UUID id) {
        this.id = id;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    /**
     * Identity is the surrogate key alone. Two instances of the same row loaded in
     * different sessions are the same entity; two unsaved instances never are.
     */
    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseEntity that)) {
            return false;
        }
        return getClass().equals(org.hibernate.Hibernate.getClass(other))
                && Objects.equals(id, that.id);
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + id + ")";
    }
}
