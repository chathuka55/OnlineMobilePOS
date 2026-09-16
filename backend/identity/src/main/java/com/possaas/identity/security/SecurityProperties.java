package com.possaas.identity.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code pos.security}.
 */
@ConfigurationProperties(prefix = "pos.security")
public record SecurityProperties(Jwt jwt, Cors cors, RateLimit rateLimit) {

    /**
     * @param secret         Base64-encoded HMAC-SHA256 key, at least 32 bytes decoded
     * @param accessTokenTtl short by design: an access token cannot be revoked, so its
     *                       blast radius is bounded by its lifetime
     * @param stepUpTtl      how long a fresh password re-entry authorises destructive
     *                       actions such as voids and refunds
     */
    public record Jwt(
            String secret,
            String issuer,
            Duration accessTokenTtl,
            Duration refreshTokenTtl,
            Duration stepUpTtl
    ) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    /**
     * @param loginAttempts        failures allowed per identity within the window
     * @param apiRequestsPerMinute per-token ceiling, generous enough for barcode-scanner
     *                             bursts on a busy till
     */
    public record RateLimit(
            int loginAttempts,
            Duration loginWindow,
            int apiRequestsPerMinute
    ) {
    }
}
