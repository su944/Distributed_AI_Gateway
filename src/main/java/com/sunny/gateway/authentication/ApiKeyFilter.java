package com.sunny.gateway.authentication;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;

final class ApiKeyFilter extends OncePerRequestFilter {
    private final ApiKeyAuthenticator authenticator;
    private final SecurityErrors errors;
    ApiKeyFilter(ApiKeyAuthenticator authenticator, SecurityErrors errors) { this.authenticator = authenticator; this.errors = errors; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return "GET".equals(request.getMethod()) && "/actuator/health".equals(request.getServletPath());
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var headers = Collections.list(request.getHeaders("Authorization"));
        if (headers.isEmpty()) { chain.doFilter(request, response); return; }
        GatewayPrincipal principal;
        try {
            String header = headers.getFirst();
            if (headers.size() != 1 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) throw new BadCredentialsException("Invalid API key");
            principal = authenticator.authenticate(header.substring(7));
        } catch (org.springframework.security.core.AuthenticationException invalid) {
            errors.write(response, HttpStatus.UNAUTHORIZED); return;
        } catch (AccessDeniedException forbidden) {
            errors.write(response, HttpStatus.FORBIDDEN); return;
        } catch (DataAccessException | org.springframework.transaction.TransactionException unavailable) {
            errors.write(response, HttpStatus.SERVICE_UNAVAILABLE); return;
        } catch (RuntimeException unexpected) {
            errors.write(response, HttpStatus.INTERNAL_SERVER_ERROR); return;
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                principal.permissions().stream().map(permission -> new SimpleGrantedAuthority(permission.name())).toList()));
        SecurityContextHolder.setContext(context);
        try { chain.doFilter(request, response); } finally { SecurityContextHolder.clearContext(); }
    }
}
