package com.possaas.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.possaas.common.api.ApiError;
import com.possaas.common.error.ApiException;
import com.possaas.common.tenant.TenantContext;
import com.possaas.tenancy.domain.Tenant;
import com.possaas.tenancy.service.TenantService;
import com.possaas.common.web.RequestContextFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Blocks writes from a tenant whose subscription has lapsed, degrading the workspace to
 * read-only rather than locking it out entirely (the shop can still see its own history).
 *
 * <p>Deliberately a request-time check against the database rather than anything baked into
 * the JWT: the token is minted at login and can live for a while, but a trial expiring or a
 * subscription lapsing mid-session must take effect on the very next write.
 */
@Component
public class SubscriptionEnforcementFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final TenantService tenantService;
    private final ObjectMapper objectMapper;

    public SubscriptionEnforcementFilter(TenantService tenantService, ObjectMapper objectMapper) {
        this.tenantService = tenantService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        UUID tenantId = TenantContext.tenantIdOrNull();
        if (tenantId != null && WRITE_METHODS.contains(request.getMethod())) {
            try {
                Tenant tenant = tenantService.requireById(tenantId);
                tenantService.assertWritable(tenant);
            } catch (ApiException ex) {
                writeError(response, ex, request.getRequestURI(),
                        RequestContextFilter.currentRequestId(request));
                return;
            }
        }
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/api/v1/platform/")
                || path.startsWith("/api/v1/subscription/")
                || path.startsWith("/api/v1/public/")
                || path.startsWith("/api/v1/webhooks/")
                || path.startsWith("/internal/");
    }

    private void writeError(HttpServletResponse response, ApiException ex, String path, String requestId)
            throws IOException {
        response.setStatus(ex.code().status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError error = new ApiError(ex.code().name(), ex.getMessage(),
                ex.details(), null, path, requestId, Instant.now());
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
