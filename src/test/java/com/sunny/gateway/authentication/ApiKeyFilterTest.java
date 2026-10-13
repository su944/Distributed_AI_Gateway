package com.sunny.gateway.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.CannotCreateTransactionException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiKeyFilterTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void failedDatabaseTransactionIsSanitizedAndDoesNotProceed() throws Exception {
        var authenticator = mock(ApiKeyAuthenticator.class);
        when(authenticator.authenticate(anyString())).thenThrow(new CannotCreateTransactionException("secret-database-password"));
        var filter = new ApiKeyFilter(authenticator, new SecurityErrors(new ObjectMapper()));
        var request = new MockHttpServletRequest("POST", "/v1/chat/completions"); request.addHeader("Authorization", "Bearer sensitive-key");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("authentication_unavailable").doesNotContain("secret-database-password", "sensitive-key");
        assertThat(chain.getRequest()).isNull();
    }
    @Test void downstreamFailureStillClearsThePrincipal() throws Exception {
        var authenticator = mock(ApiKeyAuthenticator.class);
        when(authenticator.authenticate(anyString())).thenReturn(new GatewayPrincipal(UUID.randomUUID(), UUID.randomUUID(), Set.of(Permission.CHAT_COMPLETIONS)));
        var filter = new ApiKeyFilter(authenticator, new SecurityErrors(new ObjectMapper()));
        var request = new MockHttpServletRequest("POST", "/v1/chat/completions"); request.addHeader("Authorization", "Bearer test");
        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            throw new jakarta.servlet.ServletException("downstream failed");
        })).isInstanceOf(jakarta.servlet.ServletException.class);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
