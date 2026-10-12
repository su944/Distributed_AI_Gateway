package com.sunny.gateway.provider;

/** Adapter failures are translated to a safe gateway error at the HTTP boundary. */
public class ProviderException extends RuntimeException {
    public ProviderException(String message) { super(message); }
}
