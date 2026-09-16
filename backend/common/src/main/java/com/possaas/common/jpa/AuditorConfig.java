package com.possaas.common.jpa;

import com.possaas.common.tenant.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

/**
 * Supplies the current user id for {@code @CreatedBy} / {@code @LastModifiedBy} columns.
 * Falls back to empty when no request is in flight (migrations, scheduled jobs).
 */
@Configuration
public class AuditorConfig {

    @Bean
    public AuditorAware<UUID> auditorProvider() {
        return () -> TenantContext.current().map(TenantContext.Scope::userId);
    }
}
