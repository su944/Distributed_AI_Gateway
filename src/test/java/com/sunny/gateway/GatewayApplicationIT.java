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
class GatewayApplicationIT {

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
        assertThat(client.getForEntity("/actuator/metrics", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(client.getForEntity("/actuator", String.class).getBody()).doesNotContain("/actuator/env");
    }

    @Test
    void missingResourcesUseStructuredProblemDetailsWithoutStackTraces() {
        var response = client.getForEntity("/missing-resource", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).contains("\"status\":404", "\"title\":\"Not Found\"")
                .doesNotContain("stackTrace", "java.lang", "exception");
    }
}
