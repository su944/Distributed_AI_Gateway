package com.sunny.gateway.gateway;

import com.sunny.gateway.provider.ProviderException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GatewayExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(GatewayException.class)
    ProblemDetail gatewayError(GatewayException exception) {
        return switch (exception.reason()) {
            case UNSUPPORTED_MODEL -> problem(HttpStatus.BAD_REQUEST, "unsupported_model", "The requested model is not supported.");
            case PROVIDER_UNAVAILABLE -> problem(HttpStatus.SERVICE_UNAVAILABLE, "provider_unavailable", "The requested provider is disabled or unavailable.");
        };
    }
    @ExceptionHandler(ProviderException.class)
    ProblemDetail providerError(ProviderException exception) {
        return problem(HttpStatus.BAD_GATEWAY, "provider_error", "The provider could not complete the request.");
    }
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpectedError(Exception exception) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "An unexpected error occurred.");
    }
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, problem(HttpStatus.BAD_REQUEST, "invalid_request", "Request validation failed."), headers, status, request);
    }
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, problem(HttpStatus.BAD_REQUEST, "invalid_request", "The request body is malformed or contains unsupported fields."), headers, status, request);
    }
    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
