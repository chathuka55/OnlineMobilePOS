package com.possaas.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.possaas.common.api.ApiError;
import com.possaas.common.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * HTTP security: stateless bearer authentication, method-level permission checks, and CORS
 * for the two front ends.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfiguration {

    private final SecurityProperties properties;

    public SecurityConfiguration(SecurityProperties properties) {
        this.properties = properties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter,
                                           SubscriptionEnforcementFilter subscriptionFilter,
                                           ObjectMapper objectMapper) throws Exception {
        return http
                // No cookies, no sessions, so there is no CSRF surface to protect.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/signup",
                                "/api/v1/auth/bootstrap-platform-admin",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/accept-invite",
                                "/api/v1/public/**",
                                "/api/v1/webhooks/**").permitAll()
                        .requestMatchers(
                                "/internal/actuator/health/**",
                                "/internal/actuator/info").permitAll()
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/platform/**").hasRole("PLATFORM_ADMIN")
                        .requestMatchers("/internal/actuator/**").hasRole("PLATFORM_ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter,
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(subscriptionFilter, JwtAuthenticationFilter.class)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, objectMapper, ErrorCode.UNAUTHENTICATED,
                                        "Please sign in to continue", request.getRequestURI()))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, objectMapper, ErrorCode.PERMISSION_DENIED,
                                        "You do not have permission to do that", request.getRequestURI())))
                .build();
    }

    /**
     * Argon2id with parameters tuned for an interactive login on a modest VPS: roughly
     * 100 ms per hash, which is slow enough to make offline cracking expensive but fast
     * enough that a cashier does not notice.
     *
     * <p>{@link DelegatingPasswordEncoder} keeps the {@code {argon2}} prefix in the stored
     * hash so parameters can be strengthened later without invalidating existing passwords.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(
                16,        // salt length, bytes
                32,        // hash length, bytes
                1,         // parallelism
                19 * 1024, // memory cost, 19 MiB (OWASP minimum)
                2);        // iterations
        return new DelegatingPasswordEncoder("argon2", java.util.Map.of("argon2", argon2));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Patterns, not setAllowedOrigins: Vercel mints a unique hash-suffixed URL
        // for every deployment in addition to the stable production alias (e.g.
        // mobileposonline-admin-dashboard-<hash>.vercel.app), so a literal allowlist
        // would need updating on every deploy. allowCredentials is false, so a
        // wildcard segment here only widens which origins can read a response, not
        // which origins carry ambient cookie/session auth - the JWT is sent
        // explicitly via the Authorization header.
        config.setAllowedOriginPatterns(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id",
                "X-Device-Id", "X-Idempotency-Key", "X-Outlet-Id"));
        config.setExposedHeaders(List.of("X-Request-Id", "Content-Disposition"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static void writeError(jakarta.servlet.http.HttpServletResponse response,
                                   ObjectMapper objectMapper,
                                   ErrorCode code,
                                   String message,
                                   String path) throws java.io.IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError error = new ApiError(code.name(), message, null, null, path,
                response.getHeader("X-Request-Id"), Instant.now());
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
