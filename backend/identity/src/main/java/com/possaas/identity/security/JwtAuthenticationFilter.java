package com.possaas.identity.security;

import com.possaas.common.error.ApiException;
import com.possaas.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes the security context and the tenant scope for each request.
 *
 * <p>Order matters: {@link TenantContext} must be populated before any database work
 * begins, because the datasource reads it when it binds the connection. Anything that runs
 * before this filter and touches the database sees no tenant data at all.
 *
 * <p>A malformed or expired token clears the context and lets the request continue
 * unauthenticated, so the security rules decide the outcome. That keeps 401 handling in
 * one place instead of duplicating it here.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null) {
            try {
                PosPrincipal principal = jwtService.parseAccessToken(token);
                authenticate(principal, request);
            } catch (ApiException ex) {
                // Leave the request anonymous; the authorisation layer will reject it with
                // a consistent error envelope.
                SecurityContextHolder.clearContext();
                TenantContext.clear();
                logger.debug("Rejected access token: " + ex.getMessage());
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("tenantId");
            MDC.remove("userId");
        }
    }

    private void authenticate(PosPrincipal principal, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        TenantContext.set(principal.toScope());

        if (principal.tenantId() != null) {
            MDC.put("tenantId", principal.tenantId().toString());
        }
        MDC.put("userId", principal.userId().toString());
    }

    private static String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String value = header.substring(BEARER_PREFIX.length()).trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/internal/actuator/health");
    }
}
