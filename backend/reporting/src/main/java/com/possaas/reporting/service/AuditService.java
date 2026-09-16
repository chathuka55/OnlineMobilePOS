package com.possaas.reporting.service;

import com.possaas.common.api.PageResponse;
import com.possaas.common.tenant.TenantContext;
import com.possaas.reporting.api.dto.ReportingDtos.AuditEventResponse;
import com.possaas.reporting.domain.AuditEvent;
import com.possaas.reporting.domain.AuditSeverity;
import com.possaas.reporting.repository.AuditEventRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

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
        return auditEventRepository.save(event);
    }

    @Transactional
    public AuditEvent record(String entityType, String action, String summary) {
        return record(entityType, null, null, action, AuditSeverity.INFO, summary, null, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> list(String entityType,
                                                 String action,
                                                 AuditSeverity severity,
                                                 UUID actorId,
                                                 Instant from,
                                                 Instant to,
                                                 Pageable pageable) {
        return PageResponse.of(
                auditEventRepository.search(entityType, action, severity, actorId, from, to, pageable),
                this::toResponse);
    }

    private AuditEventResponse toResponse(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getTenantId(),
                event.getOutletId(),
                event.getEntityType(),
                event.getEntityId(),
                event.getEntityNumber(),
                event.getAction(),
                event.getSeverity(),
                event.getActorId(),
                event.getActorEmail(),
                event.getActorName(),
                event.getImpersonatorId(),
                event.getSummary(),
                event.getChanges(),
                event.getMetadata(),
                event.getOccurredAt());
    }
}
