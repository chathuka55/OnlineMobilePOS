package com.possaas.tenancy.rls;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Declares the application connection pool and wraps it in {@link TenantAwareDataSource}.
 *
 * <p>Declaring both beans here means Spring Boot's own {@code DataSourceAutoConfiguration}
 * backs off, while {@code @ConfigurationProperties("spring.datasource.hikari")} keeps every
 * pool setting from {@code application.yml} in force — pool sizing, timeouts, and
 * critically {@code auto-commit: false}, which transaction-scoped tenant binding depends
 * on.
 *
 * <p>Flyway is unaffected: it is configured with its own URL and the schema-owner
 * credentials, so Boot builds it a separate, unwrapped DataSource. Migrations run with
 * full visibility while application traffic is always constrained by Row-Level Security.
 */
@Configuration
@EnableConfigurationProperties(DataSourceProperties.class)
public class TenantDataSourceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TenantDataSourceConfiguration.class);

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource applicationConnectionPool(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @Primary
    public DataSource dataSource(HikariDataSource applicationConnectionPool) {
        if (applicationConnectionPool.isAutoCommit()) {
            throw new IllegalStateException(
                    "spring.datasource.hikari.auto-commit must be false. Tenant scope is applied "
                            + "with a transaction-local setting; under autocommit it would be discarded "
                            + "immediately and every tenant-scoped query would return zero rows.");
        }
        log.info("Application DataSource is tenant-scoped via PostgreSQL Row-Level Security");
        return new TenantAwareDataSource(applicationConnectionPool);
    }
}
