package com.possaas.reporting.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.possaas.common.api.PageResponse;
import com.possaas.common.audit.AuditSeverity;
import com.possaas.common.tenant.TenantContext;
import com.possaas.reporting.api.dto.ReportingDtos.AuditEventResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read side of the audit trail (the write side, AuditService.record(), lives in
 * common.audit so write-path modules like sales/repairs/catalog can call it without a
 * circular dependency on reporting).
 *
 * <p>Built with JdbcTemplate rather than a JPA {@code @Query}: an equivalent
 * "(:from IS NULL OR occurredAt >= :from)" JPQL pattern fails at the driver level with
 * "could not determine data type of parameter" whenever from/to are actually null,
 * because Postgres can't infer a type for a parameter whose only context is an IS NULL
 * check. Only binding parameters for filters that are actually present avoids the
 * ambiguity outright.
 */
@Service
public class AuditQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditQueryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> list(String entityType,
                                                 String action,
                                                 AuditSeverity severity,
                                                 UUID actorId,
                                                 Instant from,
                                                 Instant to,
                                                 Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        StringBuilder where = new StringBuilder(" WHERE tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (entityType != null) {
            where.append(" AND entity_type = ?");
            args.add(entityType);
        }
        if (action != null) {
            where.append(" AND action = ?");
            args.add(action);
        }
        if (severity != null) {
            where.append(" AND severity = ?");
            args.add(severity.name());
        }
        if (actorId != null) {
            where.append(" AND actor_id = ?");
            args.add(actorId);
        }
        if (from != null) {
            where.append(" AND occurred_at >= ?");
            args.add(java.sql.Timestamp.from(from));
        }
        if (to != null) {
            where.append(" AND occurred_at < ?");
            args.add(java.sql.Timestamp.from(to));
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events" + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageable.getPageSize());
        pageArgs.add(pageable.getOffset());
        List<AuditEventResponse> content = jdbcTemplate.query(
                "SELECT * FROM audit_events" + where + " ORDER BY occurred_at DESC LIMIT ? OFFSET ?",
                this::mapRow, pageArgs.toArray());

        Page<AuditEventResponse> page = new PageImpl<>(content, pageable, total == null ? 0 : total);
        return PageResponse.of(page);
    }

    private AuditEventResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AuditEventResponse(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("tenant_id"),
                (UUID) rs.getObject("outlet_id"),
                rs.getString("entity_type"),
                (UUID) rs.getObject("entity_id"),
                rs.getString("entity_number"),
                rs.getString("action"),
                AuditSeverity.valueOf(rs.getString("severity")),
                (UUID) rs.getObject("actor_id"),
                rs.getString("actor_email"),
                rs.getString("actor_name"),
                (UUID) rs.getObject("impersonator_id"),
                rs.getString("summary"),
                readJson(rs.getString("changes")),
                readJson(rs.getString("metadata")),
                rs.getTimestamp("occurred_at").toInstant());
    }

    private Map<String, Object> readJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception ex) {
            return null;
        }
    }
}
