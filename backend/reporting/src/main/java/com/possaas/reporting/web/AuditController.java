package com.possaas.reporting.web;

import com.possaas.common.api.PageResponse;
import com.possaas.reporting.api.dto.ReportingDtos.AuditEventResponse;
import com.possaas.reporting.api.dto.ReportingDtos.RecordAuditRequest;
import com.possaas.reporting.domain.AuditSeverity;
import com.possaas.reporting.service.AuditService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public PageResponse<AuditEventResponse> list(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) AuditSeverity severity,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 50, sort = "occurredAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return auditService.list(entityType, action, severity, actorId, from, to, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuditEventResponse record(@RequestBody RecordAuditRequest request) {
        var event = auditService.record(
                request.entityType(),
                request.entityId(),
                request.entityNumber(),
                request.action(),
                request.severity(),
                request.summary(),
                request.changes(),
                request.metadata());
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
