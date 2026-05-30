package com.nyle.kra.revenue.support;

import java.util.UUID;

import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class PostgresIntegrationTest {

    protected static final String TEST_ADMIN_EMAIL = "phase2-admin@example.test";
    protected static final String TEST_ADMIN_PASSWORD = "test-admin-" + UUID.randomUUID();
    private static final String TEST_POSTGRES_PASSWORD = "test-postgres-" + UUID.randomUUID();
    private static final String TEST_JWT_SECRET = "test-jwt-" + UUID.randomUUID() + UUID.randomUUID();

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("phase2_test")
            .withUsername("postgres")
            .withPassword(TEST_POSTGRES_PASSWORD);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.jwt-secret", () -> TEST_JWT_SECRET);
        registry.add("app.security.default-admin-email", () -> TEST_ADMIN_EMAIL);
        registry.add("app.security.default-admin-password", () -> TEST_ADMIN_PASSWORD);
    }
}
