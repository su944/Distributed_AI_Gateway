package com.sunny.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayApplicationIT extends com.sunny.gateway.support.AuthenticatedIntegrationSupport {

    @Autowired
    private TestRestTemplate client;

    @Test
    void healthIsAvailableWithoutExposingComponents() {
        var response = client.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    void otherManagementEndpointsAreNotExposed() {
        assertThat(getAuthenticated("/actuator/metrics", String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getAuthenticated("/actuator", String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deniedRoutesUseStructuredProblemDetailsWithoutStackTraces() {
        var response = getAuthenticated("/missing-resource", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).contains("\"status\":403", "\"title\":\"Forbidden\"")
                .doesNotContain("stackTrace", "java.lang", "exception");
    }
}
