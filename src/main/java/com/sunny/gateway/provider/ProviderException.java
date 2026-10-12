package com.sunny.gateway.provider;

/** Only safe, fixed reasons are retained; vendor bodies and causes are never attached. */
public class ProviderException extends RuntimeException {
    public enum Reason { ERROR, MISSING_CREDENTIALS, AUTHENTICATION, UNAVAILABLE, TIMEOUT }
    private final Reason reason;
    public ProviderException(String message) { super(message); this.reason = Reason.ERROR; }
    public ProviderException(Reason reason) { super("Provider failure: " + reason.name()); this.reason = reason; }
    public Reason reason() { return reason; }
}
