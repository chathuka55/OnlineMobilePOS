package com.possaas.support;

import com.possaas.PosSaasApplication;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need a real PostgreSQL instance.
 *
 * <p>A real database is not optional here: the tenant isolation this product depends on is
 * implemented as PostgreSQL Row-Level Security, which an in-memory H2 cannot express. The
 * container is started once per JVM and reused across classes.
 *
 * <p>The two-role split from production is reproduced faithfully. Flyway connects as the
 * container superuser (the schema owner) while the application connects as
 * {@code possaas_app}, created by V1. Running the tests as the owner would silently bypass
 * every policy and make the isolation tests pass for the wrong reason.
 */
@SpringBootTest(classes = PosSaasApplication.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class IntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("possaas")
                    .withUsername("possaas")
                    .withPassword("possaas")
                    .withReuse(true);

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .withReuse(true);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // Migrations: schema owner.
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.flyway.placeholders.db_name", POSTGRES::getDatabaseName);
        registry.add("spring.flyway.placeholders.app_db_user", () -> "possaas_app");
        registry.add("spring.flyway.placeholders.app_db_password", () -> "possaas_app_pw");

        // Application traffic: least-privilege role subject to RLS.
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "possaas_app");
        registry.add("spring.datasource.password", () -> "possaas_app_pw");
    }
}
