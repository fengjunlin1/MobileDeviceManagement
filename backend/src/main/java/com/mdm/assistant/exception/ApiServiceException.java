package com.mdm.assistant.exception;

public class ApiServiceException extends RuntimeException {
    private final int attemptCount;
    private final boolean circuitBreakerTriggered;

    public ApiServiceException(String message, int attemptCount) {
        super(message);
        this.attemptCount = attemptCount;
        this.circuitBreakerTriggered = false;
    }

    public ApiServiceException(String message, int attemptCount, boolean circuitBreakerTriggered) {
        super(message);
        this.attemptCount = attemptCount;
        this.circuitBreakerTriggered = circuitBreakerTriggered;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public boolean isCircuitBreakerTriggered() {
        return circuitBreakerTriggered;
    }
}
