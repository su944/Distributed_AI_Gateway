package com.sunny.gateway.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.gateway.authentication.*;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {
    RedisTokenBucket buckets = mock(RedisTokenBucket.class);
    FilterChain chain = mock(FilterChain.class);
    UUID tenant = UUID.randomUUID();
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/chat/completions");
    MockHttpServletResponse response = new MockHttpServletResponse();
    @BeforeEach void authenticate() {
        request.setServletPath("/v1/chat/completions");
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new GatewayPrincipal(tenant, UUID.randomUUID(), Set.of(Permission.CHAT_COMPLETIONS)), null, List.of()));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    RateLimitFilter filter(RateLimitProperties.OutagePolicy outage) {
        return new RateLimitFilter(buckets, new RateLimitProperties(null, outage, null), new ObjectMapper());
    }
    @Test void denialStopsChainAndReturnsSanitized429() throws Exception {
        when(buckets.consume(eq(tenant), any())).thenReturn(new RedisTokenBucket.Decision(false, 0, 2, 60));
        filter(null).doFilter(request, response, chain);
        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("2");
        assertThat(response.getContentAsString()).contains("rate_limit_exceeded");
    }
    @Test void allowedRequestContinuesAndIncludesHeaders() throws Exception {
        when(buckets.consume(eq(tenant), any())).thenReturn(new RedisTokenBucket.Decision(true, 59, 0, 1));
        request.addHeader("X-Tenant-ID", UUID.randomUUID().toString());
        filter(null).doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("59");
    }
    @Test void failClosedSanitizesRedisFailure() throws Exception {
        when(buckets.consume(any(), any())).thenThrow(new IllegalStateException("secret redis host"));
        filter(null).doFilter(request, response, chain);
        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("rate_limit_unavailable").doesNotContain("secret");
    }
    @Test void explicitFailOpenContinuesWithoutLimitHeaders() throws Exception {
        when(buckets.consume(any(), any())).thenThrow(new IllegalStateException());
        filter(RateLimitProperties.OutagePolicy.FAIL_OPEN).doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        assertThat(response.getHeader("X-RateLimit-Limit")).isNull();
    }
    @Test void missingPrincipalCannotConsume() throws Exception {
        SecurityContextHolder.clearContext();
        filter(null).doFilter(request, response, chain);
        verifyNoInteractions(buckets, chain);
        assertThat(response.getStatus()).isEqualTo(401);
    }
}
