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
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "gateway_events")
@Getter
@Setter
@NoArgsConstructor
public class GatewayEvent implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = Uuid.v7();

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway", nullable = false)
    private PaymentGatewayType gateway;

    @Column(name = "external_event_id", nullable = false)
    private String externalEventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "processing_error")
    private String processingError;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt = Instant.now();

    @Transient
    private boolean persisted;

    public static GatewayEvent received(PaymentGatewayType gateway,
                                        String externalEventId,
                                        String eventType,
                                        UUID tenantId,
                                        Map<String, Object> payload) {
        GatewayEvent event = new GatewayEvent();
        event.gateway = gateway;
        event.externalEventId = externalEventId;
        event.eventType = eventType;
        event.tenantId = tenantId;
        event.payload = payload == null ? Map.of() : payload;
        event.receivedAt = Instant.now();
        return event;
    }

    public void markProcessed() {
        this.processedAt = Instant.now();
        this.processingError = null;
    }

    public void markFailed(String error) {
        this.processedAt = Instant.now();
        this.processingError = error;
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
        if (!(other instanceof GatewayEvent that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(id);
    }
}
