package com.possaas.tenancy.service;

import com.possaas.common.tenant.TenantContext;
import com.possaas.tenancy.domain.DocumentSequence;
import com.possaas.tenancy.domain.DocumentType;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Allocates human-readable document numbers such as {@code INV-2026-000431}.
 *
 * <h2>Concurrency</h2>
 * Allocation is one {@code UPDATE ... RETURNING} statement. PostgreSQL takes a row lock
 * for the duration, so two tills checking out simultaneously serialise on that row and
 * each receives a distinct number. No application-level or distributed lock is involved:
 * a Redis mutex around an already-atomic statement would add a network round trip, a new
 * failure mode when Redis is unreachable, and the risk of a lock expiring mid-transaction
 * — while providing no additional guarantee.
 *
 * <h2>Gaplessness</h2>
 * The counter advances inside the caller's transaction. If that transaction rolls back,
 * the increment rolls back with it, so numbers stay contiguous. The cost is that
 * concurrent checkouts briefly queue on the row, which is immaterial next to the audit
 * value of a sequence with no holes in it.
 */
@Service
public class DocumentNumberService {

    private final JdbcTemplate jdbcTemplate;

    public DocumentNumberService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Allocates the next number for the current tenant. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(DocumentType type) {
        return next(TenantContext.requireTenantId(), type);
    }

    /**
     * Requires an existing transaction: the allocation must commit or roll back together
     * with the document it names, otherwise a failed checkout would burn a number.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(UUID tenantId, DocumentType type) {
        String periodKey = periodKeyFor(type);
        ensureSequenceExists(tenantId, type, periodKey);

        Long allocated = jdbcTemplate.queryForObject("""
                UPDATE document_sequences
                   SET next_value = next_value + 1,
                       updated_at = now()
                 WHERE tenant_id = ?
                   AND document_type = ?
                   AND period_key = ?
                   AND outlet_id IS NULL
                RETURNING next_value - 1
                """, Long.class, tenantId, type.name(), periodKey);

        if (allocated == null) {
            throw new IllegalStateException(
                    "Failed to allocate a " + type + " number for tenant " + tenantId);
        }

        DocumentSequence template = readTemplate(tenantId, type, periodKey);
        return template.format(allocated);
    }

    /**
     * Creates the counter on first use. {@code ON CONFLICT DO NOTHING} makes this safe
     * when several terminals raise their first document of the year at once.
     */
    private void ensureSequenceExists(UUID tenantId, DocumentType type, String periodKey) {
        jdbcTemplate.update("""
                INSERT INTO document_sequences
                    (id, tenant_id, outlet_id, document_type, period_key, prefix, pad_length, next_value)
                VALUES (uuid_generate_v7(), ?, NULL, ?, ?, ?, 6, 1)
                ON CONFLICT (tenant_id, document_type, period_key) WHERE outlet_id IS NULL
                DO NOTHING
                """, tenantId, type.name(), periodKey, type.defaultPrefix());
    }

    private DocumentSequence readTemplate(UUID tenantId, DocumentType type, String periodKey) {
        return jdbcTemplate.queryForObject("""
                SELECT prefix, suffix, pad_length, period_key
                  FROM document_sequences
                 WHERE tenant_id = ?
                   AND document_type = ?
                   AND period_key = ?
                   AND outlet_id IS NULL
                """, (rs, rowNum) -> {
            DocumentSequence sequence = new DocumentSequence();
            sequence.setPrefix(rs.getString("prefix"));
            sequence.setSuffix(rs.getString("suffix"));
            sequence.setPadLength(rs.getShort("pad_length"));
            sequence.setPeriodKey(rs.getString("period_key"));
            return sequence;
        }, tenantId, type.name(), periodKey);
    }

    private String periodKeyFor(DocumentType type) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return switch (type.defaultResetPeriod()) {
            case NEVER -> "ALL";
            case YEARLY -> String.valueOf(today.getYear());
            case MONTHLY -> String.format("%d-%02d", today.getYear(), today.getMonthValue());
        };
    }
}
