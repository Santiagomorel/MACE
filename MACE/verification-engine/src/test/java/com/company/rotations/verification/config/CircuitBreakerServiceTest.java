package com.company.rotations.verification.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("CircuitBreakerService Tests")
@ExtendWith(MockitoExtension.class)
class CircuitBreakerServiceTest {

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private RetryRegistry retryRegistry;

    @Mock
    private CircuitBreaker circuitBreaker;

    @Mock
    private Retry retry;

    private CircuitBreakerService circuitBreakerService;

    @BeforeEach
    void setUp() {
        circuitBreakerService = new CircuitBreakerService(circuitBreakerRegistry, retryRegistry);
    }

    @Nested
    @DisplayName("AWS circuit breaker")
    class AwsCircuitTests {

        @Test
        @DisplayName("Should return true when AWS circuit is open")
        void awsCircuitOpen() {
            when(circuitBreakerRegistry.circuitBreaker("awsProvider")).thenReturn(circuitBreaker);
            when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);

            assertTrue(circuitBreakerService.isAwsCircuitOpen());
        }

        @Test
        @DisplayName("Should return false when AWS circuit is closed")
        void awsCircuitClosed() {
            when(circuitBreakerRegistry.circuitBreaker("awsProvider")).thenReturn(circuitBreaker);
            when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);

            assertFalse(circuitBreakerService.isAwsCircuitOpen());
        }

        @Test
        @DisplayName("Should return false when AWS circuit is half-open")
        void awsCircuitHalfOpen() {
            when(circuitBreakerRegistry.circuitBreaker("awsProvider")).thenReturn(circuitBreaker);
            when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.HALF_OPEN);

            assertFalse(circuitBreakerService.isAwsCircuitOpen());
        }

        @Test
        @DisplayName("Should return false when AWS circuit breaker is null")
        void awsCircuitNull() {
            when(circuitBreakerRegistry.circuitBreaker("awsProvider")).thenReturn(null);

            assertFalse(circuitBreakerService.isAwsCircuitOpen());
        }
    }

    @Nested
    @DisplayName("Azure circuit breaker")
    class AzureCircuitTests {

        @Test
        @DisplayName("Should return true when Azure circuit is open")
        void azureCircuitOpen() {
            when(circuitBreakerRegistry.circuitBreaker("azureProvider")).thenReturn(circuitBreaker);
            when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);

            assertTrue(circuitBreakerService.isAzureCircuitOpen());
        }

        @Test
        @DisplayName("Should return false when Azure circuit is closed")
        void azureCircuitClosed() {
            when(circuitBreakerRegistry.circuitBreaker("azureProvider")).thenReturn(circuitBreaker);
            when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);

            assertFalse(circuitBreakerService.isAzureCircuitOpen());
        }

        @Test
        @DisplayName("Should return false when Azure circuit breaker is null")
        void azureCircuitNull() {
            when(circuitBreakerRegistry.circuitBreaker("azureProvider")).thenReturn(null);

            assertFalse(circuitBreakerService.isAzureCircuitOpen());
        }
    }

    @Nested
    @DisplayName("GCP circuit breaker")
    class GcpCircuitTests {

        @Test
        @DisplayName("Should return true when GCP circuit is open")
        void gcpCircuitOpen() {
            when(circuitBreakerRegistry.circuitBreaker("gcpProvider")).thenReturn(circuitBreaker);
            when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);

            assertTrue(circuitBreakerService.isGcpCircuitOpen());
        }

        @Test
        @DisplayName("Should return false when GCP circuit is closed")
        void gcpCircuitClosed() {
            when(circuitBreakerRegistry.circuitBreaker("gcpProvider")).thenReturn(circuitBreaker);
            when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);

            assertFalse(circuitBreakerService.isGcpCircuitOpen());
        }

        @Test
        @DisplayName("Should return false when GCP circuit breaker is null")
        void gcpCircuitNull() {
            when(circuitBreakerRegistry.circuitBreaker("gcpProvider")).thenReturn(null);

            assertFalse(circuitBreakerService.isGcpCircuitOpen());
        }
    }

    @Nested
    @DisplayName("Retry configuration")
    class RetryTests {

        @Test
        @DisplayName("Should return AWS retry configuration")
        void getAwsRetry() {
            when(retryRegistry.retry("awsApiRetry")).thenReturn(retry);

            Retry result = circuitBreakerService.getAwsRetry();

            assertNotNull(result);
            verify(retryRegistry).retry("awsApiRetry");
        }

        @Test
        @DisplayName("Should return IAM API retry configuration")
        void getIamApiRetry() {
            when(retryRegistry.retry("iamApiRetry")).thenReturn(retry);

            Retry result = circuitBreakerService.getIamApiRetry();

            assertNotNull(result);
            verify(retryRegistry).retry("iamApiRetry");
        }
    }
}
