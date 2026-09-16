package com.possaas.identity.domain;

import com.possaas.common.id.Uuid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single-use token backing invitations, password resets and email verification.
 *
 * <p>Lookups are always by exact hash and the table itself is not under RLS. A
 * denormalised {@code tenantId} is stored so accept/reset flows can bind
 * {@link com.possaas.common.tenant.TenantContext} before loading the RLS-scoped user.
 */
@Entity
@Table(name = "one_time_tokens")
@Getter
@Setter
@NoArgsConstructor
public class OneTimeToken {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = Uuid.v7();

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /**
     * Denormalised from the target user so anonymous accept/reset flows can bind
     * {@link com.possaas.common.tenant.TenantContext} before the RLS-scoped user
     * row is loaded. Null for platform-operator tokens.
     */
    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, updatable = false)
    private Purpose purpose;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static OneTimeToken create(UUID userId, UUID tenantId, Purpose purpose,
                                      String tokenHash, Instant expiresAt) {
        OneTimeToken token = new OneTimeToken();
        token.userId = userId;
        token.tenantId = tenantId;
        token.purpose = purpose;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        return token;
    }

    public boolean isUsable() {
        return consumedAt == null && expiresAt.isAfter(Instant.now());
    }

    public void consume() {
        this.consumedAt = Instant.now();
    }

    public enum Purpose {
        INVITE, PASSWORD_RESET, EMAIL_VERIFY
    }
}
