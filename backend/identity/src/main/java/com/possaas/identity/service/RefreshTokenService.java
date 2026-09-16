package com.possaas.identity.service;

import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.id.Uuid;
import com.possaas.identity.domain.RefreshToken;
import com.possaas.identity.repository.RefreshTokenRepository;
import com.possaas.identity.security.JwtService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, rotates and revokes refresh tokens.
 *
 * <h2>Why both Redis and PostgreSQL</h2>
 * Redis answers the hot path — "is this token still live?" — in a single O(1) lookup, and
 * expires entries automatically. PostgreSQL keeps the durable history needed to revoke a
 * whole family, to show a user their active sessions, and to survive a Redis flush. Redis
 * is treated as a cache: if a key is missing, the database decides.
 *
 * <h2>Reuse detection</h2>
 * Every refresh invalidates the presented token. Seeing an already-rotated token means the
 * value leaked, because a well-behaved client only ever holds the newest one. The response
 * is to revoke the entire family, logging out both the attacker and the legitimate user.
 * That is deliberately aggressive: a forced re-login is a far smaller cost than a
 * persistent session hijack.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final String REDIS_KEY_PREFIX = "possaas:refresh:";

    private final RefreshTokenRepository repository;
    private final JwtService jwtService;
    private final StringRedisTemplate redis;

    public RefreshTokenService(RefreshTokenRepository repository,
                               JwtService jwtService,
                               StringRedisTemplate redis) {
        this.repository = repository;
        this.jwtService = jwtService;
        this.redis = redis;
    }

    /** Starts a new token family for a fresh sign-in. */
    @Transactional
    public Issued issueNewFamily(UUID userId, UUID tenantId, DeviceInfo device) {
        return issue(userId, tenantId, Uuid.v7(), device);
    }

    /**
     * Validates the presented token, invalidates it, and returns its replacement.
     */
    @Transactional
    public Rotated rotate(String presentedToken, DeviceInfo device) {
        String hash = jwtService.hashRefreshToken(presentedToken);

        RefreshToken existing = repository.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID,
                        "This session is no longer valid. Please sign in again."));

        if (existing.getRevokedAt() != null) {
            throw new ApiException(ErrorCode.TOKEN_INVALID,
                    "This session was signed out. Please sign in again.");
        }

        if (existing.getRotatedAt() != null) {
            // Replay of a superseded token: assume theft and burn the family.
            repository.revokeFamily(existing.getFamilyId(), "REUSE_DETECTED", Instant.now());
            evictFamilyFromCache(existing.getFamilyId());
            log.warn("Refresh token reuse detected for user {}; revoked family {}",
                    existing.getUserId(), existing.getFamilyId());
            throw new ApiException(ErrorCode.TOKEN_REUSED,
                    "For your security all sessions were signed out. Please sign in again.");
        }

        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED,
                    "Your session has expired. Please sign in again.");
        }

        existing.markRotated();
        repository.save(existing);
        redis.delete(REDIS_KEY_PREFIX + hash);

        Issued replacement = issue(existing.getUserId(), existing.getTenantId(),
                existing.getFamilyId(), device);

        return new Rotated(existing.getUserId(), existing.getTenantId(), replacement);
    }

    @Transactional
    public void revoke(String presentedToken, String reason) {
        String hash = jwtService.hashRefreshToken(presentedToken);
        repository.findByTokenHash(hash).ifPresent(token -> {
            repository.revokeFamily(token.getFamilyId(), reason, Instant.now());
            evictFamilyFromCache(token.getFamilyId());
        });
        redis.delete(REDIS_KEY_PREFIX + hash);
    }

    /** Used when a password changes or an account is disabled. */
    @Transactional
    public void revokeAllForUser(UUID userId, String reason) {
        repository.revokeAllForUser(userId, reason, Instant.now());
        repository.findByUserIdOrderByIssuedAtDesc(userId)
                .forEach(token -> redis.delete(REDIS_KEY_PREFIX + token.getTokenHash()));
    }

    private Issued issue(UUID userId, UUID tenantId, UUID familyId, DeviceInfo device) {
        String rawToken = jwtService.generateRefreshToken();
        String hash = jwtService.hashRefreshToken(rawToken);
        Duration ttl = jwtService.refreshTokenTtl();
        Instant expiresAt = Instant.now().plus(ttl);

        RefreshToken token = RefreshToken.issue(userId, tenantId, familyId, hash, expiresAt);
        if (device != null) {
            token.setDeviceId(device.deviceId());
            token.setDeviceLabel(device.label());
            token.setUserAgent(truncate(device.userAgent(), 320));
        }
        repository.save(token);

        redis.opsForValue().set(REDIS_KEY_PREFIX + hash, userId.toString(), ttl);

        return new Issued(rawToken, expiresAt, familyId);
    }

    private void evictFamilyFromCache(UUID familyId) {
        // A family is one chain per device, so listing it from the indexed durable table is
        // both cheap and safer than a Redis KEYS pattern scan.
        repository.findByFamilyId(familyId)
                .forEach(token -> redis.delete(REDIS_KEY_PREFIX + token.getTokenHash()));
    }

    /** Keeps the durable table from growing without bound. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        int removed = repository.deleteExpired(Instant.now().minus(Duration.ofDays(7)));
        if (removed > 0) {
            log.info("Purged {} expired refresh tokens", removed);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record DeviceInfo(String deviceId, String label, String userAgent) {
    }

    public record Issued(String rawToken, Instant expiresAt, UUID familyId) {
    }

    public record Rotated(UUID userId, UUID tenantId, Issued token) {
    }
}
