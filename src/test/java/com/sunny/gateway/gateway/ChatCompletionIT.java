package com.sunny.gateway.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatCompletionIT {
    @Autowired TestRestTemplate client;

    @Test void validRequestReturnsPredictableCompletion() {
        var response = post("""
            {"model":"mock-chat","messages":[{"role":"system","content":"Be brief"},{"role":"user","content":"Hello world"}]}
            """);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = response.getBody();
        assertThat(body.path("model").asText()).isEqualTo("mock-chat");
        assertThat(body.path("id").asText()).startsWith("mock-");
        assertThat(body.at("/choices/0/message/content").asText()).isEqualTo("Mock response: Hello world");
        assertThat(body.at("/usage/total_tokens").asInt()).isEqualTo(8);
        assertThat(post("""
            {"model":"mock-chat","messages":[{"role":"system","content":"Be brief"},{"role":"user","content":"Hello world"}]}
            """).getBody()).isEqualTo(body);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"model\":\"mock-chat\",\"messages\":[]}",
        "{\"model\":\"mock-chat\",\"messages\":[null]}",
        "{\"model\":\"mock-chat\",\"messages\":[{\"role\":\"tool\",\"content\":\"secret-prompt\"}]}",
        "{\"model\":\"mock-chat\",\"messages\":[{\"role\":\"user\",\"content\":\" \"}]}",
        "{\"model\":\"mock-chat\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}],\"temperature\":3}",
        "{\"model\":\"mock-chat\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}],\"max_tokens\":0}",
        "{\"model\":\"mock-chat\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}],\"stream\":true}",
        "{\"model\":\"mock-chat\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}],\"unknown\":true}",
        "{"
    })
    void invalidRequestsReturnSanitizedProblems(String json) {
        var response = post(json);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().path("code").asText()).isEqualTo("invalid_request");
        assertThat(response.getBody().toString()).doesNotContain("secret-prompt", "stackTrace", "java.lang");
    }

    @Test void unsupportedModelReturns400() {
        var response = post("""
            {"model":"unknown","messages":[{"role":"user","content":"Hello"}]}
            """);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("code").asText()).isEqualTo("unsupported_model");
    }

    private ResponseEntity<JsonNode> post(String json) {
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        return client.postForEntity("/v1/chat/completions", new HttpEntity<>(json, headers), JsonNode.class);
    }
}
