package com.possaas.common.audit;

import com.possaas.common.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes to the shared audit trail. Lives in {@code common} (rather than the
 * reporting module, where the read/query side stays) specifically so that
 * write-path services - sales, repairs, catalog, wholesale - can call
 * {@code record(...)} without a circular module dependency, since those
 * modules sit below reporting in the build graph.
 */
@Service
public class AuditService {

    private final EntityManager entityManager;

    public AuditService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Uses entityManager.persist() directly rather than JpaRepository.save():
     * every AuditEvent is unconditionally new (the table is append-only, enforced
     * by a DB trigger), but save()'s Persistable.isNew() check misidentified a
     * fresh event as existing and issued an UPDATE, which the trigger correctly
     * rejected ("audit_events is append-only"). persist() never has to guess.
     */
    @Transactional
    public AuditEvent record(String entityType,
                             UUID entityId,
                             String entityNumber,
                             String action,
                             AuditSeverity severity,
                             String summary,
                             Map<String, Object> changes,
                             Map<String, Object> metadata) {
        AuditEvent event = new AuditEvent();
        TenantContext.current().ifPresent(scope -> {
            event.setTenantId(scope.tenantId());
            event.setOutletId(scope.outletId());
            event.setActorId(scope.userId());
            event.setActorEmail(scope.userEmail());
            event.setImpersonatorId(scope.impersonatorId());
        });
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setEntityNumber(entityNumber);
        event.setAction(action);
        event.setSeverity(severity == null ? AuditSeverity.INFO : severity);
        event.setSummary(summary);
        event.setChanges(changes);
        event.setMetadata(metadata);
        event.setOccurredAt(Instant.now());
        entityManager.persist(event);
        return event;
    }

    @Transactional
    public AuditEvent record(String entityType, String action, String summary) {
        return record(entityType, null, null, action, AuditSeverity.INFO, summary, null, null);
    }
}
