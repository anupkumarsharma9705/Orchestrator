package com.example.orchestrator.model.router;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class CircuitBreaker {
    private final String providerName;
    private final int failureThreshold;
    private final long timeoutSeconds;
    private final int halfOpenRequestLimitValue;
    
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger halfOpenSuccesses = new AtomicInteger(0);
    private final AtomicInteger halfOpenRequests = new AtomicInteger(0);
    private final AtomicInteger halfOpenRequestLimit;
    private final AtomicReference<Instant> lastFailureTime = new AtomicReference<>();
    private final long timeoutMillis;

    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    public CircuitBreaker(String providerName, int failureThreshold, int timeoutSeconds, int halfOpenRequests) {
        this.providerName = providerName;
        this.failureThreshold = failureThreshold;
        this.timeoutSeconds = timeoutSeconds;
        this.timeoutMillis = timeoutSeconds * 1000L;
        this.halfOpenRequestLimitValue = halfOpenRequests;
        this.halfOpenRequestLimit = new AtomicInteger(halfOpenRequests);
    }

    public boolean allowRequest() {
        State currentState = state.get();
        
        switch (currentState) {
            case CLOSED -> {
                return true;
            }
            case OPEN -> {
                if (shouldAttemptReset()) {
                    if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                        halfOpenRequests.set(0);
                        halfOpenSuccesses.set(0);
                        halfOpenRequestLimit.set(halfOpenRequestLimitValue);
                        return true;
                    }
                    return allowRequest(); // Retry with new state
                }
                return false;
            }
            case HALF_OPEN -> {
                if (halfOpenRequests.incrementAndGet() <= halfOpenRequestLimit.get()) {
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean shouldAttemptReset() {
        Instant lastFailure = lastFailureTime.get();
        return lastFailure != null && java.time.Duration.between(lastFailure, Instant.now()).toMillis() >= timeoutMillis;
    }

    public void recordSuccess() {
        State currentState = state.get();
        
        switch (currentState) {
            case CLOSED -> {
                failureCount.set(0);
            }
            case HALF_OPEN -> {
                int successes = halfOpenSuccesses.incrementAndGet();
                if (successes >= halfOpenRequestLimitValue) {
                    state.set(State.CLOSED);
                    failureCount.set(0);
                    successCount.set(0);
                }
            }
            case OPEN -> {
                // Should not happen if allowRequest works correctly
            }
        }
    }

    public void recordFailure() {
        State currentState = state.get();
        
        switch (currentState) {
            case CLOSED -> {
                int failures = failureCount.incrementAndGet();
                lastFailureTime.set(java.time.Instant.now());
                if (failures >= failureThreshold) {
                    state.set(State.OPEN);
                }
            }
            case HALF_OPEN -> {
                state.set(State.OPEN);
                lastFailureTime.set(java.time.Instant.now());
            }
            case OPEN -> {
                lastFailureTime.set(java.time.Instant.now());
            }
        }
    }

    public State getState() {
        return state.get();
    }

    public int getFailureCount() {
        return failureCount.get();
    }

    public String getProviderName() {
        return providerName;
    }

    public void reset() {
        state.set(State.CLOSED);
        failureCount.set(0);
        successCount.set(0);
        halfOpenSuccesses.set(0);
        halfOpenRequests.set(0);
        lastFailureTime.set(null);
    }
}