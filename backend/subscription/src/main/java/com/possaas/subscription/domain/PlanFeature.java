package com.possaas.subscription.domain;

import com.possaas.common.id.Uuid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "plan_features")
@Getter
@Setter
@NoArgsConstructor
public class PlanFeature implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = Uuid.v7();

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_code", nullable = false)
    private FeatureCode featureCode;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "limit_value")
    private Integer limitValue;

    @Transient
    private boolean persisted;

    public static PlanFeature of(UUID planId, FeatureCode code) {
        PlanFeature feature = new PlanFeature();
        feature.planId = planId;
        feature.featureCode = code;
        feature.enabled = true;
        return feature;
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

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlanFeature that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(id);
    }
}
