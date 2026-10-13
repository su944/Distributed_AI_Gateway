package com.sunny.gateway.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"gateway.openai.enabled=true", "OPENAI_API_KEY="})
@ActiveProfiles("test")
class OpenAIEnabledIT extends com.sunny.gateway.support.AuthenticatedIntegrationSupport {
    @Autowired TestRestTemplate client;
    @Test void missingKeyReturns503WhileMockAndHealthStayUsable() {
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        String json = """
            {"model":"openai-chat","messages":[{"role":"user","content":"Hello"}]}
            """;
        var response = postAuthenticated("/v1/chat/completions", json, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().path("code").asText()).isEqualTo("provider_credentials_missing");
        assertThat(postAuthenticated("/v1/chat/completions", json.replace("openai-chat", "mock-chat"), JsonNode.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(client.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
