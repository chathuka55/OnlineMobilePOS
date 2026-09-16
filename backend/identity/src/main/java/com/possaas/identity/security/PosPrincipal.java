package com.possaas.identity.security;

import com.possaas.common.tenant.TenantContext;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The authenticated caller, reconstructed from the access token on every request.
 *
 * <p>Permission codes travel inside the token so authorisation needs no database round
 * trip. The trade-off is that a permission change only takes effect when the access token
 * next rotates, which is at most {@code accessTokenTtl}.
 */
public record PosPrincipal(
        UUID userId,
        String email,
        String fullName,
        UUID tenantId,
        String tenantSlug,
        UUID outletId,
        Set<String> roles,
        Set<String> permissions,
        boolean platformAdmin,
        UUID impersonatorId,
        Instant stepUpVerifiedAt,
        String deviceId
) {

    public Collection<? extends GrantedAuthority> authorities() {
        return java.util.stream.Stream.concat(
                        roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)),
                        permissions.stream().map(SimpleGrantedAuthority::new))
                .toList();
    }

    public boolean has(String permission) {
        return permissions.contains(permission);
    }

    public boolean isImpersonating() {
        return impersonatorId != null;
    }

    /** True when a password was re-entered recently enough to authorise a sensitive action. */
    public boolean hasFreshStepUp(java.time.Duration validFor) {
        return stepUpVerifiedAt != null
                && stepUpVerifiedAt.plus(validFor).isAfter(Instant.now());
    }

    public TenantContext.Scope toScope() {
        return new TenantContext.Scope(
                tenantId, tenantSlug, outletId, userId, email,
                impersonatorId, platformAdmin, false);
    }
}
