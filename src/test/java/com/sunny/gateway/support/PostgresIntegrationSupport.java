package com.sunny.gateway.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** One real database per test JVM; Ryuk cleans it up after verification. */
public abstract class PostgresIntegrationSupport {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("gateway_test").withUsername("gateway_test").withPassword("testcontainers-only-password");
    static { POSTGRES.start(); }
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("GATEWAY_API_KEY_PEPPER", () -> "test-only-pepper-32-characters-or-longer");
    }
}
