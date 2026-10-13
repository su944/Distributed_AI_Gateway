package com.sunny.gateway.support;

import com.sunny.gateway.authentication.*;
import com.sunny.gateway.tenant.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.util.Set;
import java.util.UUID;

public abstract class AuthenticatedIntegrationSupport extends PostgresIntegrationSupport {
    @Autowired private TenantService tenants;
    @Autowired private ApiKeyService keys;
    @Autowired private TestRestTemplate authenticatedClient;
    private String key;
    @BeforeEach void provisionKey() {
        String slug = "test-" + UUID.randomUUID();
        var tenant = tenants.create(slug, slug);
        key = keys.issue(tenant.id(), "test", Set.of(Permission.CHAT_COMPLETIONS), null).plaintext();
    }
    protected <T> ResponseEntity<T> getAuthenticated(String url, Class<T> type) {
        return authenticatedClient.exchange(url, HttpMethod.GET, new HttpEntity<>(headers()), type);
    }
    protected <T> ResponseEntity<T> postAuthenticated(String url, String json, Class<T> type) {
        return authenticatedClient.exchange(url, HttpMethod.POST, new HttpEntity<>(json, headers()), type);
    }
    private HttpHeaders headers() {
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON); headers.setBearerAuth(key); return headers;
    }
}
