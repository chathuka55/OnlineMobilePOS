package com.possaas.common.tenant;

import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import java.util.Optional;
import java.util.UUID;

/**
 * The tenant, outlet and actor bound to the current request.
 *
 * <p>Populated once by the authentication filter from a verified JWT, then read by the
 * datasource hook that issues {@code SET LOCAL app.tenant_id} and by entity listeners
 * stamping {@code tenant_id} on new rows.
 *
 * <p>Backed by a {@link ScopedValue}-style {@link ThreadLocal} that inherits across
 * virtual threads, which matters because the API runs on Loom
 * ({@code spring.threads.virtual.enabled}). Anything dispatched to a plain
 * {@code ExecutorService} must re-establish the scope explicitly via
 * {@link #runAs(Scope, Runnable)}.
 */
public final class TenantContext {

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    /**
     * @param tenantId null for platform-level requests, which is what makes the RLS
     *                 policies resolve to the platform-scoped rows
     */
    public record Scope(
            UUID tenantId,
            String tenantSlug,
            UUID outletId,
            UUID userId,
            String userEmail,
            UUID impersonatorId,
            boolean platformAdmin,
            boolean readOnly
    ) {

        public static Scope forTenant(UUID tenantId, String slug, UUID outletId, UUID userId, String email) {
            return new Scope(tenantId, slug, outletId, userId, email, null, false, false);
        }

        public static Scope forPlatform(UUID userId, String email) {
            return new Scope(null, null, null, userId, email, null, true, false);
        }

        public Scope withReadOnly(boolean value) {
            return new Scope(tenantId, tenantSlug, outletId, userId, userEmail,
                    impersonatorId, platformAdmin, value);
        }

        public Scope withOutlet(UUID value) {
            return new Scope(tenantId, tenantSlug, value, userId, userEmail,
                    impersonatorId, platformAdmin, readOnly);
        }

        public Scope impersonating(UUID tenantId, String slug, UUID actingUserId) {
            return new Scope(tenantId, slug, null, actingUserId, userEmail,
                    userId, platformAdmin, readOnly);
        }
    }

    public static void set(Scope scope) {
        CURRENT.set(scope);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Optional<Scope> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** The tenant id, or null when the request is platform-scoped or unauthenticated. */
    public static UUID tenantIdOrNull() {
        Scope scope = CURRENT.get();
        return scope == null ? null : scope.tenantId();
    }

    /**
     * The tenant id, failing loudly when absent. Used by code that cannot meaningfully
     * proceed without a tenant, so a missing scope surfaces as a clear 500 rather than
     * as a silently empty result set from RLS.
     */
    public static UUID requireTenantId() {
        UUID tenantId = tenantIdOrNull();
        if (tenantId == null) {
            throw new ApiException(ErrorCode.TENANT_CONTEXT_MISSING,
                    "No tenant is bound to the current request");
        }
        return tenantId;
    }

    public static UUID userIdOrNull() {
        Scope scope = CURRENT.get();
        return scope == null ? null : scope.userId();
    }

    public static UUID requireOutletId() {
        Scope scope = CURRENT.get();
        if (scope == null || scope.outletId() == null) {
            throw new ApiException(ErrorCode.TENANT_CONTEXT_MISSING,
                    "No outlet is bound to the current request");
        }
        return scope.outletId();
    }

    public static boolean isPlatformAdmin() {
        Scope scope = CURRENT.get();
        return scope != null && scope.platformAdmin();
    }

    public static boolean isReadOnly() {
        Scope scope = CURRENT.get();
        return scope != null && scope.readOnly();
    }

    /** Runs work under an explicit scope, restoring whatever was previously bound. */
    public static void runAs(Scope scope, Runnable action) {
        Scope previous = CURRENT.get();
        try {
            CURRENT.set(scope);
            action.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
