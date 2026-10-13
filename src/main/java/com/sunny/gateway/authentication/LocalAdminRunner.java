package com.sunny.gateway.authentication;

import com.sunny.gateway.tenant.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.*;

/** Explicit non-web local provisioning only. No admin HTTP routes or plaintext logging. */
@Component
@Profile("local & !prod")
@ConditionalOnProperty(name = "gateway.admin.command")
public class LocalAdminRunner implements ApplicationRunner {
    private final TenantService tenants;
    private final ApiKeyService keys;
    private final Environment environment;
    private final ConfigurableApplicationContext context;
    public LocalAdminRunner(TenantService tenants, ApiKeyService keys, Environment environment, ConfigurableApplicationContext context) {
        this.tenants = tenants; this.keys = keys; this.environment = environment; this.context = context;
    }
    @Override public void run(ApplicationArguments arguments) throws Exception {
        if (!"none".equalsIgnoreCase(environment.getProperty("spring.main.web-application-type"))) {
            throw new IllegalStateException("Local admin commands require non-web mode");
        }
        try {
            switch (required("command")) {
                case "tenant-create" -> System.out.println(tenants.create(required("name"), required("slug")));
                case "tenant-list" -> tenants.list().forEach(System.out::println);
                case "tenant-status" -> tenants.changeStatus(tenantId(), Tenant.Status.valueOf(required("status")));
                case "key-list" -> keys.list(tenantId()).forEach(System.out::println);
                case "key-revoke" -> keys.revoke(tenantId(), UUID.fromString(required("key-id")));
                case "key-issue" -> issueToFile();
                default -> throw new IllegalArgumentException("Unknown local admin command");
            }
        } finally { context.close(); }
    }
    private void issueToFile() throws Exception {
        var file = Path.of(required("output-file"));
        // No overwrite, no plaintext stdout, and permissions are restrictive at file creation.
        // Parent directory must already exist and be controlled by the operator.
        Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        String expiry = environment.getProperty("gateway.admin.expires-at");
        var permissions = "none".equals(environment.getProperty("gateway.admin.permissions"))
                ? Set.<Permission>of() : Set.of(Permission.CHAT_COMPLETIONS);
        var issued = keys.issue(tenantId(), required("name"), permissions, expiry == null ? null : Instant.parse(expiry));
        Files.writeString(file, issued.plaintext() + System.lineSeparator(), StandardOpenOption.WRITE);
        System.out.println(issued.metadata());
        System.out.println("New API key written once to an owner-only file.");
    }
    private UUID tenantId() { return UUID.fromString(required("tenant-id")); }
    private String required(String name) {
        String value = environment.getProperty("gateway.admin." + name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing local admin option: " + name);
        return value;
    }
}
