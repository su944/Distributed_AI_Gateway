package com.sunny.gateway.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class SecurityErrors {
    private final ObjectMapper mapper;
    public SecurityErrors(ObjectMapper mapper) { this.mapper = mapper; }
    public void write(HttpServletResponse response, HttpStatus status) throws IOException {
        String code = switch (status) { case UNAUTHORIZED -> "unauthorized"; case FORBIDDEN -> "forbidden"; case INTERNAL_SERVER_ERROR -> "internal_error"; default -> "authentication_unavailable"; };
        String detail = switch (status) { case UNAUTHORIZED -> "A valid gateway API key is required."; case FORBIDDEN -> "Access is denied."; case INTERNAL_SERVER_ERROR -> "An unexpected error occurred."; default -> "Authentication is temporarily unavailable."; };
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        response.setStatus(status.value()); response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        if (status == HttpStatus.UNAUTHORIZED) response.setHeader("WWW-Authenticate", "Bearer");
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
