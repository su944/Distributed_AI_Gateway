package com.sunny.gateway.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.gateway.authentication.GatewayPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Registered only in the security chain, after authorization and before MVC. */
public final class RateLimitFilter extends OncePerRequestFilter {
    private final RedisTokenBucket buckets;
    private final RateLimitProperties properties;
    private final ObjectMapper mapper;
    public RateLimitFilter(RedisTokenBucket buckets, RateLimitProperties properties, ObjectMapper mapper) {
        this.buckets = buckets; this.properties = properties; this.mapper = mapper;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !"/v1/chat/completions".equals(request.getServletPath());
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof GatewayPrincipal principal)) {
            problem(response, HttpStatus.UNAUTHORIZED, "unauthorized", "A valid gateway API key is required.");
            return;
        }
        var policy = properties.policy(principal.tenantId());
        RedisTokenBucket.Decision decision;
        try {
            decision = buckets.consume(principal.tenantId(), policy);
        } catch (RuntimeException unavailable) {
            if (properties.outagePolicy() == RateLimitProperties.OutagePolicy.FAIL_OPEN) {
                chain.doFilter(request, response);
            } else {
                problem(response, HttpStatus.SERVICE_UNAVAILABLE, "rate_limit_unavailable", "Rate limiting is temporarily unavailable.");
            }
            return;
        }
        response.setHeader("X-RateLimit-Limit", Long.toString(policy.capacity()));
        response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", Long.toString(decision.resetAfter()));
        if (!decision.allowed()) {
            response.setHeader("Retry-After", Long.toString(decision.retryAfter()));
            problem(response, HttpStatus.TOO_MANY_REQUESTS, "rate_limit_exceeded", "Tenant request rate limit exceeded.");
            return;
        }
        chain.doFilter(request, response);
    }
    private void problem(HttpServletResponse response, HttpStatus status, String code, String detail) throws IOException {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
