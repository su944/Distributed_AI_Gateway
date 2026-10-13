package com.sunny.gateway.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.gateway.gateway.*;
import com.sunny.gateway.provider.ProviderException;
import com.sunny.gateway.routing.*;
import com.sunny.gateway.authentication.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Set;
import java.util.UUID;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OpenAIProviderIT {
    private static final String KEY = "fake-test-key-do-not-use";
    private static final String GATEWAY_REQUEST = """
        {"model":"openai-chat","messages":[{"role":"system","content":"Be brief"},{"role":"user","content":"Hello"},{"role":"assistant","content":"Earlier"}],"temperature":0.2,"max_tokens":128}
        """;
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private ExecutorService executor;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private int status = 200;
    private String response = OpenAIMapperTest.SUCCESS;
    private long delayMillis;
    private boolean reset;

    @BeforeEach void startStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool(); server.setExecutor(executor);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            path.set(exchange.getRequestURI().toString()); method.set(exchange.getRequestMethod());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                if (delayMillis > 0) Thread.sleep(delayMillis);
                if (!reset) {
                    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally { exchange.close(); }
        });
        server.start();
    }
    @AfterEach void stopStub() { server.stop(0); executor.shutdownNow(); SecurityContextHolder.clearContext(); }

    @Test void successfulHttpCallSendsBearerKeyAndProviderRequest() throws Exception {
        mvc(provider(Duration.ofSeconds(2), Duration.ofSeconds(3), KEY)).perform(post("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON).content(GATEWAY_REQUEST))
                .andExpect(status().isOk()).andExpect(jsonPath("$.model").value("openai-chat"))
                .andExpect(jsonPath("$.id").value("chatcmpl-test"))
                .andExpect(jsonPath("$.choices[0].message.content").value("Hello back"))
                .andExpect(jsonPath("$.usage.total_tokens").value(10));
        assertThat(authorization.get()).isEqualTo("Bearer " + KEY);
        assertThat(path.get()).isEqualTo("/v1/chat/completions");
        assertThat(method.get()).isEqualTo("POST");
        assertThat(contentType.get()).startsWith("application/json");
        JsonNode outbound = json.readTree(body.get());
        assertThat(outbound.path("model").asText()).isEqualTo("vendor-model");
        assertThat(outbound.path("max_completion_tokens").asInt()).isEqualTo(128);
        assertThat(outbound.path("messages").size()).isEqualTo(3);
        assertThat(outbound.at("/messages/0/role").asText()).isEqualTo("system");
        assertThat(outbound.at("/messages/1/role").asText()).isEqualTo("user");
        assertThat(outbound.at("/messages/2/role").asText()).isEqualTo("assistant");
        assertThat(body.get()).doesNotContain(KEY);
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test void explicitRoutingModelBypassesLegacyAliasMapAndPreservesGatewayAlias() throws Exception {
        var provider = provider(Duration.ofSeconds(2), Duration.ofSeconds(3), KEY);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new GatewayPrincipal(UUID.randomUUID(), UUID.randomUUID(), Set.of(Permission.CHAT_COMPLETIONS)), null, List.of()));
        var candidate = new RoutingProperties.Candidate("openai", "another-upstream-model", 0, 1, true);
        var route = new RoutingProperties.Model(RoutingProperties.Strategy.PRIORITY, true, List.of(candidate));
        var properties = new RoutingProperties(Map.of("team-chat", route),
                new RoutingProperties.TenantPolicy(Set.of("team-chat"), Set.of("openai")), null);
        var config = new OpenAIProperties(true, Map.of("openai-chat", "vendor-model"),
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3));
        var routing = new RoutingService(new ModelRegistry(properties, config, List.of(provider)),
                List.of(new PriorityRoutingStrategy(), new WeightedRandomRoutingStrategy(bound -> 0)), new SimpleMeterRegistry());
        var mvc = MockMvcBuilders.standaloneSetup(new ChatCompletionController(new GatewayService(routing)))
                .setControllerAdvice(new GatewayExceptionHandler()).build();
        mvc.perform(post("/v1/chat/completions").contentType(MediaType.APPLICATION_JSON)
                .content(GATEWAY_REQUEST.replace("openai-chat", "team-chat")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.model").value("team-chat"));
        assertThat(json.readTree(body.get()).path("model").asText()).isEqualTo("another-upstream-model");
        assertThat(requests.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"400,502,provider_error", "401,503,provider_authentication_error", "403,503,provider_authentication_error",
            "429,503,provider_unavailable", "500,502,provider_error", "503,503,provider_unavailable", "302,502,provider_error"})
    void httpErrorsAreSanitizedAndNeverRetried(int upstream, int expected, String code) throws Exception {
        status = upstream; response = "{\"error\":\"" + KEY + " sensitive-provider-body\"}";
        assertProblem(provider(Duration.ofSeconds(2), Duration.ofSeconds(3), KEY), expected, code);
        assertThat(requests.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{}", "", "{\"id\":\"x\",\"choices\":[]}"})
    void malformedAndEmptyResponsesReturn502(String payload) throws Exception {
        response = payload;
        assertProblem(provider(Duration.ofSeconds(2), Duration.ofSeconds(3), KEY), 502, "provider_error");
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test void overallDeadlineMapsTo504AndDoesNotRetry() throws Exception {
        delayMillis = 1000;
        assertProblem(provider(Duration.ofSeconds(2), Duration.ofMillis(300), KEY), 504, "provider_timeout");
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test void responseTimeoutMapsTo504() throws Exception {
        delayMillis = 1000;
        assertProblem(provider(Duration.ofMillis(300), Duration.ofSeconds(3), KEY), 504, "provider_timeout");
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test void connectionResetIsNotReplayedByTransport() throws Exception {
        reset = true;
        assertProblem(provider(Duration.ofSeconds(2), Duration.ofSeconds(3), KEY), 502, "provider_error");
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test void refusedConnectionMapsTo502() throws Exception {
        var provider = provider(Duration.ofSeconds(2), Duration.ofSeconds(3), KEY);
        server.stop(0);
        assertProblem(provider, 502, "provider_error");
        assertThat(requests.get()).isZero();
    }

    @Test void missingCredentialsAreClearAndNeverSendRequest() throws Exception {
        assertProblem(provider(Duration.ofSeconds(2), Duration.ofSeconds(3), " "), 503, "provider_credentials_missing");
        assertThat(requests.get()).isZero();
    }

    @Test void adapterExceptionsRetainNoSensitiveBodyOrCause() throws Exception {
        status = 401; response = KEY + " sensitive-provider-body";
        var request = json.readValue(GATEWAY_REQUEST, ChatCompletionRequest.class);
        assertThatThrownBy(() -> provider(Duration.ofSeconds(2), Duration.ofSeconds(3), KEY).complete(request))
                .isInstanceOf(ProviderException.class).hasNoCause()
                .hasMessageNotContaining(KEY).hasMessageNotContaining("sensitive-provider-body");
    }

    private OpenAIProvider provider(Duration responseTimeout, Duration requestTimeout, String key) {
        var properties = new OpenAIProperties(true, Map.of("openai-chat", "vendor-model"),
                Duration.ofSeconds(1), responseTimeout, requestTimeout);
        // Use the production transport configuration; change only destination to the local stub.
        var client = new OpenAIConfiguration().openAIWebClient(properties).mutate()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build();
        return new OpenAIProvider(client, properties, key, new OpenAIMapper());
    }
    private MockMvc mvc(OpenAIProvider provider) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new GatewayPrincipal(UUID.randomUUID(), UUID.randomUUID(), Set.of(Permission.CHAT_COMPLETIONS)), null, List.of()));
        var config = new OpenAIProperties(true, Map.of("openai-chat", "vendor-model"),
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3));
        var routing = new RoutingService(new ModelRegistry(new RoutingProperties(null, null, null), config, List.of(provider)),
                List.of(new PriorityRoutingStrategy(), new WeightedRandomRoutingStrategy(bound -> 0)), new SimpleMeterRegistry());
        return MockMvcBuilders.standaloneSetup(new ChatCompletionController(new GatewayService(routing)))
                .setControllerAdvice(new GatewayExceptionHandler()).build();
    }
    private void assertProblem(OpenAIProvider provider, int expectedStatus, String code) throws Exception {
        var result = mvc(provider).perform(post("/v1/chat/completions").contentType(MediaType.APPLICATION_JSON)
                .content(GATEWAY_REQUEST)).andExpect(status().is(expectedStatus))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code)).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(KEY, "sensitive-provider-body", "stackTrace");
    }
}
