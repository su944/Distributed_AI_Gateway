package com.sunny.gateway.gateway;

public class GatewayException extends RuntimeException {
    public enum Reason { UNSUPPORTED_MODEL, PROVIDER_UNAVAILABLE, MODEL_FORBIDDEN }
    private final Reason reason;
    public GatewayException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }
    public Reason reason() { return reason; }
}
