package com.company.rotations.alerting.controller;

import com.company.rotations.alerting.AlertMetricsCollector;
import com.company.rotations.alerting.pipeline.WebhookPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Controller integration test verifying delegation to WebhookPipeline.
 * Tests the complete flow: webhook -> controller -> pipeline -> response.
 * (Task 4.17, 4.18)
 */
class WebhookPipelineFullTest {

    private WebhookPipeline webhookPipeline;
    private AlertMetricsCollector metricsCollector;
    private WebhookController controller;

    @BeforeEach
    void setUp() {
        webhookPipeline = mock(WebhookPipeline.class);
        metricsCollector = mock(AlertMetricsCollector.class);
        controller = new WebhookController(webhookPipeline, metricsCollector);
    }

    @Nested
    @DisplayName("Controller - Happy Path Delegation")
    class ControllerHappyPathTests {

        @Test
        @DisplayName("Should delegate to pipeline and return accepted response")
        void shouldDelegatetoPipeline() throws Exception {
            WebhookPipeline.PipelineResult expectedResult = new WebhookPipeline.PipelineResult(
                    WebhookPipeline.PipelineResult.Status.ACCEPTED,
                    Map.of("status", "accepted", "event_id", "evt-123", "source", "gitguardian", "request_id", "req"),
                    HttpStatus.OK);

            when(webhookPipeline.execute(eq("gitguardian"), any(), anyString())).thenReturn(expectedResult);

            Map<String, Object> payload = Map.of(
                    "source", "gitguardian",
                    "id", "evt-123",
                    "incident", Map.of(
                            "id", "gg-456",
                            "repository", "my/repo",
                            "secret_type", "aws_access_key",
                            "value_hash", "sha256abc"
                    )
            );

            ResponseEntity<?> result = controller.handleWebhook(payload, null);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("accepted", body.get("status"));
            assertEquals("evt-123", body.get("event_id"));

            verify(webhookPipeline).execute(eq("gitguardian"), any(), anyString());
            verify(metricsCollector).recordWebhookReceived();
            verify(metricsCollector).recordSource("gitguardian");
        }
    }

    @Nested
    @DisplayName("Controller - Backpressure Handling (HTTP 429)")
    class ControllerBackpressureTests {

        @Test
        @DisplayName("Should return HTTP 429 when pipeline returns backpressure")
        void shouldReturn429OnBackpressure() throws Exception {
            WebhookPipeline.PipelineResult expectedResult = new WebhookPipeline.PipelineResult(
                    WebhookPipeline.PipelineResult.Status.BACKPRESSURE,
                    Map.of("status", "backpressure", "message", "Server busy, alert dropped", "request_id", "req"),
                    HttpStatus.TOO_MANY_REQUESTS);

            when(webhookPipeline.execute(eq("gitguardian"), any(), anyString())).thenReturn(expectedResult);

            Map<String, Object> payload = Map.of("source", "gitguardian", "id", "evt-backpressure");

            ResponseEntity<?> result = controller.handleWebhook(payload, null);

            assertEquals(HttpStatus.TOO_MANY_REQUESTS, result.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("backpressure", body.get("status"));

            verify(webhookPipeline).execute(eq("gitguardian"), any(), anyString());
        }
    }

    @Nested
    @DisplayName("Controller - Auth Failure Handling")
    class ControllerAuthFailureTests {

        @Test
        @DisplayName("Should return HTTP 401 on signature validation failure")
        void shouldReturn401OnSignatureFailure() throws Exception {
            WebhookPipeline.PipelineResult expectedResult = new WebhookPipeline.PipelineResult(
                    WebhookPipeline.PipelineResult.Status.SIGNATURE_INVALID,
                    Map.of("error", "INVALID_SIGNATURE", "message", "Webhook signature validation failed", "request_id", "req"),
                    HttpStatus.UNAUTHORIZED);

            when(webhookPipeline.execute(eq("gitguardian"), any(), anyString())).thenReturn(expectedResult);

            Map<String, Object> payload = Map.of("source", "gitguardian", "id", "evt-1");

            ResponseEntity<?> result = controller.handleWebhook(payload, null);

            assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("INVALID_SIGNATURE", body.get("error"));

            verify(webhookPipeline).execute(eq("gitguardian"), any(), anyString());
        }

        @Test
        @DisplayName("Should return HTTP 403 on IP blocked")
        void shouldReturn403OnIpBlocked() throws Exception {
            WebhookPipeline.PipelineResult expectedResult = new WebhookPipeline.PipelineResult(
                    WebhookPipeline.PipelineResult.Status.IP_FORBIDDEN,
                    Map.of("error", "IP_FORBIDDEN", "message", "Request from untrusted IP address", "request_id", "req"),
                    HttpStatus.FORBIDDEN);

            when(webhookPipeline.execute(eq("gitguardian"), any(), anyString())).thenReturn(expectedResult);

            Map<String, Object> payload = Map.of("source", "gitguardian", "id", "evt-2");

            ResponseEntity<?> result = controller.handleWebhook(payload, null);

            assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());

            verify(webhookPipeline).execute(eq("gitguardian"), any(), anyString());
        }
    }

    @Nested
    @DisplayName("Controller - Dedup Handling")
    class ControllerDedupTests {

        @Test
        @DisplayName("Should return duplicate_skipped when event dedup hit")
        void shouldReturnDuplicateSkipped() throws Exception {
            WebhookPipeline.PipelineResult expectedResult = new WebhookPipeline.PipelineResult(
                    WebhookPipeline.PipelineResult.Status.DUPLICATE_SKIPPED,
                    Map.of("status", "duplicate_skipped", "source_event_id", "dup-event", "request_id", "req"),
                    HttpStatus.OK);

            when(webhookPipeline.execute(eq("gitguardian"), any(), anyString())).thenReturn(expectedResult);

            Map<String, Object> payload = Map.of("source", "gitguardian", "id", "dup-event");

            ResponseEntity<?> result = controller.handleWebhook(payload, null);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("duplicate_skipped", body.get("status"));

            verify(webhookPipeline).execute(eq("gitguardian"), any(), anyString());
        }

        @Test
        @DisplayName("Should return secret_dedup_cooldown status")
        void shouldReturnSecretDedupCooldown() throws Exception {
            WebhookPipeline.PipelineResult expectedResult = new WebhookPipeline.PipelineResult(
                    WebhookPipeline.PipelineResult.Status.SECRET_COOLDOWN,
                    Map.of("status", "secret_dedup_cooldown", "source_event_id", "evt-cooldown", "request_id", "req"),
                    HttpStatus.OK);

            when(webhookPipeline.execute(eq("gitguardian"), any(), anyString())).thenReturn(expectedResult);

            Map<String, Object> payload = Map.of(
                    "source", "gitguardian",
                    "id", "evt-cooldown",
                    "incident", Map.of("value_hash", "h1", "repository", "r1")
            );

            ResponseEntity<?> result = controller.handleWebhook(payload, null);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("secret_dedup_cooldown", body.get("status"));
        }

        @Test
        @DisplayName("Should return secret_in_progress status")
        void shouldReturnSecretInProgress() throws Exception {
            WebhookPipeline.PipelineResult expectedResult = new WebhookPipeline.PipelineResult(
                    WebhookPipeline.PipelineResult.Status.SECRET_IN_PROGRESS,
                    Map.of("status", "secret_in_progress", "source_event_id", "evt-progress", "request_id", "req"),
                    HttpStatus.OK);

            when(webhookPipeline.execute(eq("gitguardian"), any(), anyString())).thenReturn(expectedResult);

            Map<String, Object> payload = Map.of(
                    "source", "gitguardian",
                    "id", "evt-progress",
                    "incident", Map.of("value_hash", "h2")
            );

            ResponseEntity<?> result = controller.handleWebhook(payload, null);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("secret_in_progress", body.get("status"));
        }
    }

    @Nested
    @DisplayName("Controller - Error Handling")
    class ControllerErrorTests {

        @Test
        @DisplayName("Should return 500 when pipeline throws unexpected exception")
        void shouldReturn500OnUnexpectedError() throws Exception {
            when(webhookPipeline.execute(eq("gitguardian"), any(), anyString()))
                    .thenThrow(new RuntimeException("Unexpected error"));

            Map<String, Object> payload = Map.of("source", "gitguardian", "id", "evt-error");

            ResponseEntity<?> result = controller.handleWebhook(payload, null);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("error", body.get("status"));

            verify(metricsCollector).recordWebhookReceived();
            verify(metricsCollector).recordAlertFailed();
        }

        @Test
        @DisplayName("Should return processing_failed when pipeline encounters error")
        void shouldReturnProcessingFailed() throws Exception {
            WebhookPipeline.PipelineResult expectedResult = new WebhookPipeline.PipelineResult(
                    WebhookPipeline.PipelineResult.Status.PROCESSING_FAILED,
                    Map.of("status", "processing_failed", "message", "Alert sent to dead letter queue", "request_id", "req"),
                    HttpStatus.OK);

            when(webhookPipeline.execute(eq("gitguardian"), any(), anyString())).thenReturn(expectedResult);

            Map<String, Object> payload = Map.of(
                    "source", "gitguardian",
                    "id", "evt-error",
                    "incident", Map.of("value_hash", "h3")
            );

            ResponseEntity<?> result = controller.handleWebhook(payload, null);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("processing_failed", body.get("status"));

            verify(webhookPipeline).execute(eq("gitguardian"), any(), anyString());
        }
    }
}
