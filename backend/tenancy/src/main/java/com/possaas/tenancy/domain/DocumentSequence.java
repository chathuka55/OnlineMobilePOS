package com.possaas.tenancy.domain;

import com.possaas.common.id.Uuid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-tenant counter behind human-readable document numbers such as {@code INV-2026-000431}.
 *
 * <p>Replaces {@code "BILL-" + System.currentTimeMillis()} from the desktop app, which
 * produced numbers nobody could audit and which could collide when two terminals rang up
 * a sale in the same millisecond.
 *
 * <p>Deliberately not a {@code BaseEntity}: allocation is a single
 * {@code UPDATE ... RETURNING} under a row lock, so it needs neither optimistic locking
 * (which would turn concurrent tills into retry storms) nor an audit trail.
 */
@Entity
@Table(name = "document_sequences")
@Getter
@Setter
@NoArgsConstructor
public class DocumentSequence {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = Uuid.v7();

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "outlet_id")
    private UUID outletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private DocumentType documentType;

    @Column(name = "period_key", nullable = false)
    private String periodKey = "ALL";

    @Column(name = "prefix", nullable = false)
    private String prefix = "";

    @Column(name = "suffix", nullable = false)
    private String suffix = "";

    @Column(name = "pad_length", nullable = false)
    private short padLength = 6;

    @Column(name = "next_value", nullable = false)
    private long nextValue = 1;

    public static DocumentSequence create(UUID tenantId, UUID outletId,
                                          DocumentType type, String periodKey) {
        DocumentSequence sequence = new DocumentSequence();
        sequence.tenantId = tenantId;
        sequence.outletId = outletId;
        sequence.documentType = type;
        sequence.periodKey = periodKey;
        sequence.prefix = type.defaultPrefix();
        return sequence;
    }

    /**
     * Renders a number for the given counter value, for example
     * {@code INV-2026-000431} for prefix {@code INV}, period {@code 2026} and value 431.
     */
    public String format(long value) {
        StringBuilder builder = new StringBuilder();
        if (!prefix.isBlank()) {
            builder.append(prefix).append('-');
        }
        if (!"ALL".equals(periodKey)) {
            builder.append(periodKey).append('-');
        }
        builder.append(String.format("%0" + padLength + "d", value));
        if (!suffix.isBlank()) {
            builder.append('-').append(suffix);
        }
        return builder.toString();
    }
}
