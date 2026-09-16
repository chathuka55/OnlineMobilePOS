package com.possaas.subscription.domain;

import com.possaas.common.id.Uuid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "usage_counters")
@Getter
@Setter
@NoArgsConstructor
public class UsageCounter implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = Uuid.v7();

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric", nullable = false)
    private UsageMetric metric;

    /** Period bucket, e.g. {@code 2026-07} for monthly metrics. */
    @Column(name = "period_key", nullable = false)
    private String periodKey;

    @Column(name = "counter_value", nullable = false)
    private long counterValue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Transient
    private boolean persisted;

    public static UsageCounter create(UUID tenantId, UsageMetric metric, String periodKey) {
        UsageCounter counter = new UsageCounter();
        counter.tenantId = tenantId;
        counter.metric = metric;
        counter.periodKey = periodKey;
        counter.counterValue = 0;
        return counter;
    }

    public void increment(long delta) {
        this.counterValue += delta;
    }

    @Override
    public UUID getId() {
        return id;
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
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UsageCounter that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(id);
    }
}
