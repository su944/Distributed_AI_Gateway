package com.sunny.gateway.ratelimit;

import com.sunny.gateway.DistributedAiGatewayApplication;
import com.sunny.gateway.authentication.*;
import com.sunny.gateway.provider.MockLLMProvider;
import com.sunny.gateway.support.PostgresIntegrationSupport;
import com.sunny.gateway.tenant.TenantService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "gateway.rate-limit.defaults.capacity=2", "gateway.rate-limit.defaults.refill-rate=0.1"})
@ActiveProfiles("test")
class RateLimitIT extends PostgresIntegrationSupport {
    @Autowired TestRestTemplate client;
    @Autowired TenantService tenants;
    @Autowired ApiKeyService keys;
    @Autowired RedisTokenBucket buckets;
    @Autowired StringRedisTemplate redis;
    @MockitoSpyBean MockLLMProvider provider;
    UUID tenant;
    String key;
    @BeforeEach void provision() {
        String slug = "rate-" + UUID.randomUUID();
        tenant = tenants.create(slug, slug).id();
        key = keys.issue(tenant, "chat", Set.of(Permission.CHAT_COMPLETIONS), null).plaintext();
    }
    ResponseEntity<String> chat(TestRestTemplate http, String bearer) {
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null) headers.setBearerAuth(bearer);
        headers.set("X-Tenant-ID", UUID.randomUUID().toString());
        return http.postForEntity("/v1/chat/completions", new HttpEntity<>(
                "{\"model\":\"mock-chat\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}", headers), String.class);
    }
    @Test void capacityReturns429AndRejectedRequestsNeverInvokeProvider() {
        assertThat(chat(client, key).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(chat(client, key).getStatusCode()).isEqualTo(HttpStatus.OK);
        clearInvocations(provider);
        var denied = chat(client, key);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(Long.parseLong(denied.getHeaders().getFirst("Retry-After"))).isBetween(1L, 10L);
        assertThat(denied.getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(denied.getBody()).contains("rate_limit_exceeded");
        verify(provider, never()).complete(any());
        verify(provider, never()).complete(any(), anyString());
    }
    @Test void invalidAndUnpermittedKeysCannotConsumeTokens() {
        assertThat(chat(client, "invalid").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chat(client, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String unpermitted = keys.issue(tenant, "restricted", Set.of(), null).plaintext();
        assertThat(chat(client, unpermitted).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(redis.hasKey(RedisTokenBucket.key(tenant))).isFalse();
        assertThat(chat(client, key).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test void keysShareTenantBucketAndTenantsAreIndependent() {
        String sibling = keys.issue(tenant, "sibling", Set.of(Permission.CHAT_COMPLETIONS), null).plaintext();
        assertThat(chat(client, key).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(chat(client, sibling).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(chat(client, key).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        String slug = "other-rate-" + UUID.randomUUID();
        String other = keys.issue(tenants.create(slug, slug).id(), "other", Set.of(Permission.CHAT_COMPLETIONS), null).plaintext();
        assertThat(chat(client, other).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test void fractionalRefillCostAndTtlWorkWithServerTime() throws Exception {
        var policy = new RateLimitProperties.Policy(2, 4, 2);
        assertThat(buckets.consume(tenant, policy).allowed()).isTrue();
        assertThat(buckets.consume(tenant, policy).allowed()).isFalse();
        assertThat(redis.getExpire(RedisTokenBucket.key(tenant), TimeUnit.MILLISECONDS)).isBetween(1L, 1100L);
        Thread.sleep(600);
        assertThat(buckets.consume(tenant, policy).allowed()).isTrue();
    }
    @Test void backwardServerTimestampCannotCreateTokens() {
        var policy = new RateLimitProperties.Policy(2, 1, 1);
        buckets.consume(tenant, policy);
        redis.opsForHash().put(RedisTokenBucket.key(tenant), "updated_at", Long.toString(System.currentTimeMillis() + 60000));
        assertThat(buckets.consume(tenant, policy).allowed()).isTrue();
        assertThat(buckets.consume(tenant, policy).allowed()).isFalse();
        assertThat(redis.getExpire(RedisTokenBucket.key(tenant))).isGreaterThan(50);
    }
    @Test void independentRedisClientsCannotOverspendUnderConcurrentConsumption() throws Exception {
        var connection = new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        connection.afterPropertiesSet();
        try {
            var other = new RedisTokenBucket(new StringRedisTemplate(connection));
            var policy = new RateLimitProperties.Policy(8, 0.01, 1);
            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(16)) {
                List<Future<Boolean>> outcomes = new ArrayList<>();
                for (int i = 0; i < 64; i++) {
                    final RedisTokenBucket selected = i % 2 == 0 ? buckets : other;
                    outcomes.add(executor.submit(() -> { start.await(); return selected.consume(tenant, policy).allowed(); }));
                }
                start.countDown();
                int allowed = 0;
                for (var outcome : outcomes) if (outcome.get(10, TimeUnit.SECONDS)) allowed++;
                assertThat(allowed).isEqualTo(8);
            }
        } finally { connection.destroy(); }
    }
    @Test void actualRedisOutageFollowsBothPoliciesOverHttp() {
        var isolatedRedis = new org.testcontainers.containers.GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
        isolatedRedis.start();
        int port = isolatedRedis.getMappedPort(6379);
        String host = isolatedRedis.getHost();
        try {
            try (var closed = outageApplication(host, port, "FAIL_CLOSED")) {
                var http = httpFor(closed);
                assertThat(chat(http, key).getStatusCode()).isEqualTo(HttpStatus.OK);
                isolatedRedis.stop();
                var response = chat(http, key);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(response.getBody()).contains("rate_limit_unavailable").doesNotContain("Redis", "Exception");
                assertThat(response.getHeaders().getFirst("X-RateLimit-Limit")).isNull();
            }
            try (var open = outageApplication(host, port, "FAIL_OPEN")) {
                var response = chat(httpFor(open), key);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getHeaders().getFirst("X-RateLimit-Limit")).isNull();
            }
        } finally { isolatedRedis.stop(); }
    }
    private org.springframework.context.ConfigurableApplicationContext outageApplication(String host, int port, String policy) {
        return new SpringApplicationBuilder(DistributedAiGatewayApplication.class).profiles("test").run(
                "--server.port=0", "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(), "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--GATEWAY_API_KEY_PEPPER=test-only-pepper-32-characters-or-longer",
                "--spring.data.redis.host=" + host, "--spring.data.redis.port=" + port,
                "--spring.data.redis.timeout=200ms", "--spring.data.redis.connect-timeout=200ms",
                "--gateway.rate-limit.outage-policy=" + policy);
    }
    private TestRestTemplate httpFor(org.springframework.context.ConfigurableApplicationContext context) {
        var http = new TestRestTemplate();
        http.setUriTemplateHandler(new org.springframework.web.util.DefaultUriBuilderFactory(
                "http://127.0.0.1:" + ((ServletWebServerApplicationContext) context).getWebServer().getPort()));
        return http;
    }
    @Test void twoGatewayInstancesEnforceOneBucketUnderConcurrentHttpRequests() throws Exception {
        try (var second = new SpringApplicationBuilder(DistributedAiGatewayApplication.class).profiles("test").run(
                "--server.port=0", "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(), "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--GATEWAY_API_KEY_PEPPER=test-only-pepper-32-characters-or-longer",
                "--spring.data.redis.host=" + REDIS.getHost(), "--spring.data.redis.port=" + REDIS.getMappedPort(6379),
                "--gateway.rate-limit.defaults.capacity=2", "--gateway.rate-limit.defaults.refill-rate=0.1")) {
            var other = new TestRestTemplate();
            String root = "http://127.0.0.1:" + ((ServletWebServerApplicationContext) second).getWebServer().getPort();
            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(12)) {
                List<Future<HttpStatusCode>> responses = new ArrayList<>();
                for (int i = 0; i < 12; i++) {
                    final boolean useFirst = i % 2 == 0;
                    responses.add(executor.submit(() -> {
                        start.await();
                        if (useFirst) return chat(client, key).getStatusCode();
                        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON); headers.setBearerAuth(key);
                        return other.postForEntity(root + "/v1/chat/completions", new HttpEntity<>(
                                "{\"model\":\"mock-chat\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}", headers), String.class).getStatusCode();
                    }));
                }
                start.countDown();
                List<HttpStatusCode> statuses = new ArrayList<>();
                for (var response : responses) statuses.add(response.get(15, TimeUnit.SECONDS));
                assertThat(statuses).filteredOn(s -> s.equals(HttpStatus.OK)).hasSize(2);
                assertThat(statuses).filteredOn(s -> s.equals(HttpStatus.TOO_MANY_REQUESTS)).hasSize(10);
            }
        }
    }
}
