package com.possaas.reporting.repository;

import com.possaas.reporting.domain.AuditEvent;
import com.possaas.reporting.domain.AuditSeverity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    @Query("""
            SELECT e FROM AuditEvent e
             WHERE (:entityType IS NULL OR e.entityType = :entityType)
               AND (:action IS NULL OR e.action = :action)
               AND (:severity IS NULL OR e.severity = :severity)
               AND (:actorId IS NULL OR e.actorId = :actorId)
               AND (:from IS NULL OR e.occurredAt >= :from)
               AND (:to IS NULL OR e.occurredAt < :to)
             ORDER BY e.occurredAt DESC
            """)
    Page<AuditEvent> search(@Param("entityType") String entityType,
                            @Param("action") String action,
                            @Param("severity") AuditSeverity severity,
                            @Param("actorId") UUID actorId,
                            @Param("from") Instant from,
                            @Param("to") Instant to,
                            Pageable pageable);
}
