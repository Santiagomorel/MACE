package com.company.rotations.verification.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.stereotype.Service;

@Service
public class CircuitBreakerService {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    public CircuitBreakerService(CircuitBreakerRegistry circuitBreakerRegistry,
                                  RetryRegistry retryRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
    }

    public boolean isAwsCircuitOpen() {
        return getCircuitBreaker("awsProvider").map(CircuitBreaker::getState)
                .map(state -> state == CircuitBreaker.State.OPEN)
                .orElse(false);
    }

    public boolean isAzureCircuitOpen() {
        return getCircuitBreaker("azureProvider").map(CircuitBreaker::getState)
                .map(state -> state == CircuitBreaker.State.OPEN)
                .orElse(false);
    }

    public boolean isGcpCircuitOpen() {
        return getCircuitBreaker("gcpProvider").map(CircuitBreaker::getState)
                .map(state -> state == CircuitBreaker.State.OPEN)
                .orElse(false);
    }

    public Retry getAwsRetry() {
        return retryRegistry.retry("awsApiRetry");
    }

    public Retry getIamApiRetry() {
        return retryRegistry.retry("iamApiRetry");
    }

    private java.util.Optional<CircuitBreaker> getCircuitBreaker(String name) {
        return java.util.Optional.ofNullable(circuitBreakerRegistry.circuitBreaker(name));
    }
}
