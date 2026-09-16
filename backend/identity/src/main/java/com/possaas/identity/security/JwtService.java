package com.possaas.identity.security;

import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.id.Uuid;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies access tokens, and mints the opaque refresh tokens.
 *
 * <h2>Two token types, on purpose</h2>
 * The access token is a signed JWT so authorisation is a signature check with no database
 * hit. It is short-lived because there is no way to revoke one mid-flight. The refresh
 * token is an opaque random string, never a JWT: it must be revocable, and only its
 * SHA-256 hash is ever persisted so a leaked database cannot be replayed as a session.
 */
@Service
public class JwtService {

    private static final String CLAIM_TENANT_ID = "tid";
    private static final String CLAIM_TENANT_SLUG = "tsl";
    private static final String CLAIM_OUTLET_ID = "oid";
    private static final String CLAIM_EMAIL = "eml";
    private static final String CLAIM_NAME = "nam";
    private static final String CLAIM_ROLES = "rls";
    private static final String CLAIM_PERMISSIONS = "prm";
    private static final String CLAIM_PLATFORM_ADMIN = "adm";
    private static final String CLAIM_IMPERSONATOR = "imp";
    private static final String CLAIM_STEP_UP_AT = "sup";
    private static final String CLAIM_DEVICE_ID = "dev";

    private static final int REFRESH_TOKEN_BYTES = 48;

    private final SecretKey signingKey;
    private final SecurityProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(SecurityProperties properties) {
        this.properties = properties;
        byte[] keyBytes = decodeSecret(properties.jwt().secret());
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "pos.security.jwt.secret must decode to at least 32 bytes for HMAC-SHA256. "
                            + "Generate one with: openssl rand -base64 48");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // --- access tokens ---------------------------------------------------------------

    public IssuedAccessToken issueAccessToken(PosPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.jwt().accessTokenTtl());

        String token = Jwts.builder()
                .issuer(properties.jwt().issuer())
                .subject(principal.userId().toString())
                .id(Uuid.v7().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim(CLAIM_EMAIL, principal.email())
                .claim(CLAIM_NAME, principal.fullName())
                .claim(CLAIM_TENANT_ID, asString(principal.tenantId()))
                .claim(CLAIM_TENANT_SLUG, principal.tenantSlug())
                .claim(CLAIM_OUTLET_ID, asString(principal.outletId()))
                .claim(CLAIM_ROLES, List.copyOf(principal.roles()))
                .claim(CLAIM_PERMISSIONS, List.copyOf(principal.permissions()))
                .claim(CLAIM_PLATFORM_ADMIN, principal.platformAdmin())
                .claim(CLAIM_IMPERSONATOR, asString(principal.impersonatorId()))
                .claim(CLAIM_STEP_UP_AT, principal.stepUpVerifiedAt() == null
                        ? null : principal.stepUpVerifiedAt().getEpochSecond())
                .claim(CLAIM_DEVICE_ID, principal.deviceId())
                .signWith(signingKey)
                .compact();

        return new IssuedAccessToken(token, expiry,
                properties.jwt().accessTokenTtl().toSeconds());
    }

    public PosPrincipal parseAccessToken(String token) {
        Claims claims = parseClaims(token);

        Instant stepUpAt = claims.get(CLAIM_STEP_UP_AT) == null
                ? null
                : Instant.ofEpochSecond(((Number) claims.get(CLAIM_STEP_UP_AT)).longValue());

        return new PosPrincipal(
                UUID.fromString(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_NAME, String.class),
                Uuid.parseOrNull(claims.get(CLAIM_TENANT_ID, String.class)),
                claims.get(CLAIM_TENANT_SLUG, String.class),
                Uuid.parseOrNull(claims.get(CLAIM_OUTLET_ID, String.class)),
                stringSet(claims.get(CLAIM_ROLES)),
                stringSet(claims.get(CLAIM_PERMISSIONS)),
                Boolean.TRUE.equals(claims.get(CLAIM_PLATFORM_ADMIN, Boolean.class)),
                Uuid.parseOrNull(claims.get(CLAIM_IMPERSONATOR, String.class)),
                stepUpAt,
                claims.get(CLAIM_DEVICE_ID, String.class));
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.jwt().issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            // Distinguished from a malformed token so the client knows to refresh rather
            // than to prompt for credentials again.
            throw new ApiException(ErrorCode.TOKEN_EXPIRED, "Your session has expired");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.TOKEN_INVALID, "Invalid authentication token");
        }
    }

    // --- refresh tokens -------------------------------------------------------------

    /** A 384-bit URL-safe random string. Opaque by design: it carries no claims. */
    public String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public java.time.Duration refreshTokenTtl() {
        return properties.jwt().refreshTokenTtl();
    }

    public java.time.Duration stepUpTtl() {
        return properties.jwt().stepUpTtl();
    }

    private static byte[] decodeSecret(String secret) {
        try {
            return Decoders.BASE64.decode(secret);
        } catch (RuntimeException ex) {
            // Tolerate a raw passphrase so a misconfigured dev environment still boots.
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    private static String asString(UUID value) {
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private static Set<String> stringSet(Object claim) {
        if (claim instanceof List<?> list) {
            return new LinkedHashSet<>((List<String>) list);
        }
        return Set.of();
    }

    public record IssuedAccessToken(String token, Instant expiresAt, long expiresInSeconds) {
    }
}
