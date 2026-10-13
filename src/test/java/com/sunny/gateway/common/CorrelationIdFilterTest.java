package com.sunny.gateway.common;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class CorrelationIdFilterTest {
    @Test void generatesServerIdAndCleansMdcEvenWhenDownstreamFails() {
        var request = new MockHttpServletRequest(); request.addHeader("X-Request-ID", "client-secret");
        var response = new MockHttpServletResponse();
        assertThatThrownBy(() -> new CorrelationIdFilter().doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("correlation_id")).isEqualTo(response.getHeader("X-Request-ID"));
            assertThat(UUID.fromString(MDC.get("correlation_id"))).isNotNull();
            throw new jakarta.servlet.ServletException("test failure");
        })).isInstanceOf(jakarta.servlet.ServletException.class);
        assertThat(MDC.get("correlation_id")).isNull();
        assertThat(response.getHeader("X-Request-ID")).isNotEqualTo("client-secret");
    }
}
