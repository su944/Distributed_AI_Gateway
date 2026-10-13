package com.sunny.gateway.authentication;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunny.gateway.support.PostgresIntegrationSupport;
import com.sunny.gateway.tenant.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class AuthenticationIT extends PostgresIntegrationSupport {
    @Autowired TestRestTemplate client;
    @Autowired TenantService tenants;
    @Autowired ApiKeyService keys;
    @Autowired ApiKeyAuthenticator authenticator;
    @Autowired JdbcTemplate jdbc;
    private UUID tenantId;
    private ApiKeyService.IssuedKey key;
    @BeforeEach void provision() {
        String slug = "security-" + UUID.randomUUID();
        tenantId = tenants.create(slug, slug).id();
        key = keys.issue(tenantId, "primary", Set.of(Permission.CHAT_COMPLETIONS), null);
    }
    @Test void migrationAndSchemaValidationSucceedOnEmptyPostgres() {
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success = true", Integer.class)).isEqualTo(1);
    }
    @Test void validKeySucceedsAndLastUsedIsRecordedWithoutSession() {
        var response = chat(key.plaintext());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().at("/choices/0/message/content").asText()).isEqualTo("Mock response: Hello");
        assertThat(response.getHeaders().get("Set-Cookie")).isNull();
        assertThat(keys.get(tenantId, key.metadata().id()).lastUsedAt()).isNotNull();
        var principal = authenticator.authenticate(key.plaintext());
        assertThat(principal.tenantId()).isEqualTo(tenantId);
        assertThat(principal.keyId()).isEqualTo(key.metadata().id());
        assertThat(principal.permissions()).containsExactly(Permission.CHAT_COMPLETIONS);
    }
    @Test void missingMalformedWrongUnknownAndDigestKeysReturn401() {
        for (String bearer : Arrays.asList(null, "", "not-a-key", key.plaintext().substring(0, 79) + "!",
                key.plaintext().substring(0, 37) + "A".repeat(43), "dag_" + "0".repeat(32) + "_" + "A".repeat(43),
                jdbc.queryForObject("select secret_hash from api_keys where id = ?", String.class, key.metadata().id()))) {
            var response = chat(bearer);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().path("code").asText()).isEqualTo("unauthorized");
            assertThat(response.getHeaders().getFirst("WWW-Authenticate")).isEqualTo("Bearer");
            assertThat(response.getBody().toString()).doesNotContain(key.plaintext());
        }
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Authorization", "Basic bogus");
        assertThat(send(headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        headers.setBearerAuth(key.plaintext()); headers.add("Authorization", "Bearer " + key.plaintext());
        assertThat(send(headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    @Test void expiredAndRevokedKeysAreUnauthorized() {
        jdbc.update("update api_keys set created_at = ?, expires_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(100)), java.sql.Timestamp.from(Instant.now().minusSeconds(1)), key.metadata().id());
        assertThat(chat(key.plaintext()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var revoked = keys.issue(tenantId, "revoked", Set.of(Permission.CHAT_COMPLETIONS), null);
        keys.revoke(tenantId, revoked.metadata().id());
        assertThat(chat(revoked.plaintext()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    @Test void suspendedTenantAndUnpermittedKeyAreForbidden() {
        var noPermission = keys.issue(tenantId, "restricted", Set.of(), null);
        assertThat(chat(noPermission.plaintext()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        tenants.changeStatus(tenantId, Tenant.Status.SUSPENDED);
        assertThat(chat(key.plaintext()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    @Test void publicHealthAndDeniedManagementRemainSanitized() {
        assertThat(client.getForEntity("/actuator/health", String.class).getBody()).isEqualTo("{\"status\":\"UP\"}");
        for (String path : List.of("/actuator", "/actuator/env", "/admin/keys", "/tenants", "/api-keys")) {
            assertThat(client.getForEntity(path, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            var headers = new HttpHeaders(); headers.setBearerAuth(key.plaintext());
            assertThat(client.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
    @Test void databaseContainsOnlyDigestAndMetadataNeverReturnsSecret(org.springframework.boot.test.system.CapturedOutput captured) {
        var row = jdbc.queryForMap("select * from api_keys where id = ?", key.metadata().id());
        assertThat(row.get("secret_hash").toString()).matches("[a-f0-9]{64}");
        assertThat(row.values().toString()).doesNotContain(key.plaintext());
        assertThat(keys.list(tenantId).toString()).doesNotContain(key.plaintext(), row.get("secret_hash").toString());
        assertThat(key.toString()).doesNotContain(key.plaintext());
        assertThat(captured.getAll()).doesNotContain(key.plaintext(), row.get("secret_hash").toString());
    }
    @Test void tenantScopedQueriesRejectCrossTenantReadsAndRevocation() {
        String slug = "other-" + UUID.randomUUID(); var other = tenants.create(slug, slug);
        var otherKey = keys.issue(other.id(), "other", Set.of(Permission.CHAT_COMPLETIONS), null);
        var principal = authenticator.authenticate(key.plaintext());
        assertThat(keys.list(principal.tenantId())).extracting(ApiKeyService.KeyMetadata::id).doesNotContain(otherKey.metadata().id());
        assertThatThrownBy(() -> keys.get(principal.tenantId(), otherKey.metadata().id())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> keys.revoke(principal.tenantId(), otherKey.metadata().id())).isInstanceOf(IllegalArgumentException.class);
        var headers = new HttpHeaders(); headers.setBearerAuth(key.plaintext()); headers.set("X-Tenant-ID", other.id().toString());
        assertThat(send(headers).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authenticator.authenticate(key.plaintext()).tenantId()).isEqualTo(tenantId);
        assertThat(chat(otherKey.plaintext()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test void issueNewThenRevokeOldRotationWorks() {
        var replacement = keys.issue(tenantId, "replacement", Set.of(Permission.CHAT_COMPLETIONS), Instant.now().plusSeconds(3600));
        assertThat(chat(key.plaintext()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(chat(replacement.plaintext()).getStatusCode()).isEqualTo(HttpStatus.OK);
        keys.revoke(tenantId, key.metadata().id());
        keys.revoke(tenantId, key.metadata().id()); // idempotent revocation
        assertThat(chat(key.plaintext()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chat(replacement.plaintext()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(keys.get(tenantId, key.metadata().id()).revokedAt()).isNotNull();
    }
    @Test void securityContextDoesNotLeakAcrossSequentialOrConcurrentRequests() throws Exception {
        assertThat(chat(key.plaintext()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(chat(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<Future<HttpStatusCode>>();
            for (int i = 0; i < 12; i++) {
                final boolean valid = i % 2 == 0;
                futures.add(executor.submit(() -> chat(valid ? key.plaintext() : null).getStatusCode()));
            }
            for (int i = 0; i < futures.size(); i++) assertThat(futures.get(i).get()).isEqualTo(i % 2 == 0 ? HttpStatus.OK : HttpStatus.UNAUTHORIZED);
        }
        assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
    @Test void uniqueTenantIdentifiersAndExpiryValidationAreEnforced() {
        String slug = tenants.list().stream().filter(t -> t.id().equals(tenantId)).findFirst().orElseThrow().slug();
        assertThatThrownBy(() -> tenants.create("duplicate", slug)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> keys.issue(tenantId, "bad", Set.of(), Instant.now().minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void localIssuanceWritesPlaintextOnceToOwnerOnlyFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory, org.springframework.boot.test.system.CapturedOutput captured) throws Exception {
        var output = directory.resolve("new.key");
        var env = new org.springframework.mock.env.MockEnvironment()
                .withProperty("spring.main.web-application-type", "none")
                .withProperty("gateway.admin.command", "key-issue")
                .withProperty("gateway.admin.tenant-id", tenantId.toString())
                .withProperty("gateway.admin.name", "cli-key")
                .withProperty("gateway.admin.output-file", output.toString());
        var runner = new LocalAdminRunner(tenants, keys, env, org.mockito.Mockito.mock(org.springframework.context.ConfigurableApplicationContext.class));
        runner.run(new org.springframework.boot.DefaultApplicationArguments());
        String issued = java.nio.file.Files.readString(output).strip();
        assertThat(chat(issued).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(captured.getAll()).doesNotContain(issued);
        assertThat(java.nio.file.Files.getPosixFilePermissions(output)).isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        assertThatThrownBy(() -> runner.run(new org.springframework.boot.DefaultApplicationArguments())).isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
    }
    private ResponseEntity<JsonNode> chat(String bearer) {
        var headers = new HttpHeaders();
        if (bearer != null) headers.set("Authorization", "Bearer " + bearer);
        return send(headers);
    }
    private ResponseEntity<JsonNode> send(HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        return client.postForEntity("/v1/chat/completions", new HttpEntity<>("""
            {"model":"mock-chat","messages":[{"role":"user","content":"Hello"}]}
            """, headers), JsonNode.class);
    }
}
