package com.possaas.identity.domain;

import com.possaas.common.id.Uuid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Durable record of an issued refresh token.
 *
 * <p>Only the SHA-256 hash is stored, so a database dump cannot be replayed as a session.
 * Live tokens are also mirrored in Redis for fast validation; this table is the audit
 * trail and the reuse detector.
 *
 * <h2>Rotation and theft detection</h2>
 * Each refresh mints a new token in the same {@code familyId} and marks the old one
 * rotated. A rotated token being presented again means the value leaked — the legitimate
 * client would only ever hold the newest one — so the entire family is revoked and both
 * the attacker and the victim are forced to sign in again. Losing a session is a small
 * price for containing a stolen token.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = Uuid.v7();

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    /** Groups every token descended from one successful sign-in. */
    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "device_label")
    private String deviceLabel;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason")
    private String revokedReason;

    public static RefreshToken issue(UUID userId, UUID tenantId, UUID familyId,
                                     String tokenHash, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.userId = userId;
        token.tenantId = tenantId;
        token.familyId = familyId;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        return token;
    }

    public boolean isUsable() {
        return revokedAt == null
                && rotatedAt == null
                && expiresAt.isAfter(Instant.now());
    }

    public void markRotated() {
        this.rotatedAt = Instant.now();
    }

    public void revoke(String reason) {
        this.revokedAt = Instant.now();
        this.revokedReason = reason;
    }
}
