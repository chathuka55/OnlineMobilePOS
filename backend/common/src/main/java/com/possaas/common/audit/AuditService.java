package com.possaas.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.possaas.common.id.Uuid;
import com.possaas.common.tenant.TenantContext;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes to the shared audit trail. Lives in {@code common} (rather than the
 * reporting module, where the read/query side stays) specifically so that
 * write-path services - sales, repairs, catalog, wholesale - can call
 * {@code record(...)} without a circular module dependency, since those
 * modules sit below reporting in the build graph.
 *
 * <p>Built with JdbcTemplate rather than a JPA entity: audit_events is
 * append-only (enforced by a DB trigger), and going through
 * JpaRepository.save() / entityManager.persist() for this entity
 * intermittently produced an UPDATE instead of an INSERT for reasons that
 * didn't reproduce consistently across call sites, which the trigger then
 * correctly rejected. A plain parameterised INSERT has no entity-state
 * ambiguity to get wrong, and mirrors AuditQueryService's read side, which
 * already uses JdbcTemplate against this same table.
 */
@Service
public class AuditService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public RecordedEvent record(String entityType,
                                UUID entityId,
                                String entityNumber,
                                String action,
                                AuditSeverity severity,
                                String summary,
                                Map<String, Object> changes,
                                Map<String, Object> metadata) {
        TenantContext.Scope scope = TenantContext.current().orElse(null);
        final UUID tenantId = scope == null ? null : scope.tenantId();
        final UUID outletId = scope == null ? null : scope.outletId();
        final UUID actorId = scope == null ? null : scope.userId();
        final String actorEmail = scope == null ? null : scope.userEmail();
        final UUID impersonatorId = scope == null ? null : scope.impersonatorId();
        final UUID id = Uuid.v7();
        final Instant occurredAt = Instant.now();
        final AuditSeverity effectiveSeverity = severity == null ? AuditSeverity.INFO : severity;

        jdbcTemplate.update("""
                INSERT INTO audit_events
                    (id, tenant_id, outlet_id, entity_type, entity_id, entity_number,
                     action, severity, actor_id, actor_email, impersonator_id,
                     summary, changes, metadata, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
                """,
                ps -> {
                    ps.setObject(1, id);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, outletId);
                    ps.setString(4, entityType);
                    ps.setObject(5, entityId);
                    ps.setString(6, entityNumber);
                    ps.setString(7, action);
                    ps.setString(8, effectiveSeverity.name());
                    ps.setObject(9, actorId);
                    ps.setString(10, actorEmail);
                    ps.setObject(11, impersonatorId);
                    ps.setString(12, summary);
                    ps.setString(13, toJson(changes));
                    ps.setString(14, toJson(metadata));
                    ps.setObject(15, java.sql.Timestamp.from(occurredAt));
                });

        return new RecordedEvent(id, tenantId, outletId, entityType, entityId, entityNumber,
                action, effectiveSeverity, actorId, actorEmail, impersonatorId, summary,
                changes, metadata, occurredAt);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public RecordedEvent record(String entityType, String action, String summary) {
        return record(entityType, null, null, action, AuditSeverity.INFO, summary, null, null);
    }

    /** What record() just wrote, for callers (like the generic POST endpoint) that need it back. */
    public record RecordedEvent(
            UUID id,
            UUID tenantId,
            UUID outletId,
            String entityType,
            UUID entityId,
            String entityNumber,
            String action,
            AuditSeverity severity,
            UUID actorId,
            String actorEmail,
            UUID impersonatorId,
            String summary,
            Map<String, Object> changes,
            Map<String, Object> metadata,
            Instant occurredAt
    ) {
    }

    private String toJson(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }
}
