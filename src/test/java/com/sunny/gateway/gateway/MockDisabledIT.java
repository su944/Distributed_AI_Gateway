package com.sunny.gateway.gateway;

import com.sunny.gateway.provider.LLMProvider;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "gateway.mock.enabled=false")
@ActiveProfiles("test")
class MockDisabledIT {
    @Autowired TestRestTemplate client;
    @Autowired ApplicationContext context;
    @Test void disabledProviderLeavesContextHealthyAndReturns503() {
        assertThat(context.getBeansOfType(LLMProvider.class)).isEmpty();
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        var response = client.postForEntity("/v1/chat/completions", new HttpEntity<>("""
            {"model":"mock-chat","messages":[{"role":"user","content":"Hello"}]}
            """, headers), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().path("code").asText()).isEqualTo("provider_unavailable");
        assertThat(client.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
