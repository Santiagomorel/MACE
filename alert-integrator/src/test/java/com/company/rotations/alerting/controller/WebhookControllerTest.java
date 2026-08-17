package com.company.rotations.alerting.controller;

import com.company.rotations.alerting.AlertMetricsCollector;
import com.company.rotations.alerting.pipeline.WebhookPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Controller unit tests verifying delegation to WebhookPipeline.
 * The pipeline's internal logic is tested by WebhookPipelineFullTest.
 * This test focuses on controller responsibilities: source detection,
 * metrics recording, and response pass-through.
 * (Task 4.18)
 */
class WebhookControllerTest {

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
    @DisplayName("Source Detection")
    class SourceTests {

        @Test
        @DisplayName("Should detect source from source field")
        void shouldDetectFromSource() {
            WebhookPipeline.PipelineResult expectedResult = new WebhookPipeline.PipelineResult(
                    WebhookPipeline.PipelineResult.Status.ACCEPTED,
                    Map.of("status", "accepted"), HttpStatus.OK);
            when(webhookPipeline.execute(eq("gitguardian"), any(), anyString())).thenReturn(expectedResult);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("source", "gitguardian");
            payload.put("id", "evt-1");

            controller.handleWebhook(payload, null);

            verify(webhookPipeline).execute(eq("gitguardian"), any(), anyString());
            verify(metricsCollector).recordSource("gitguardian");
        }

        @Test
        @DisplayName("Should detect source from trigger field when source missing")
        void shouldDetectFromTrigger() {
            WebhookPipeline.PipelineResult expectedResult = new WebhookPipeline.PipelineResult(
                    WebhookPipeline.PipelineResult.Status.ACCEPTED,
                    Map.of("status", "accepted"), HttpStatus.OK);
            when(webhookPipeline.execute(eq("trigger-src"), any(), anyString())).thenReturn(expectedResult);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("trigger", "trigger-src");
            payload.put("id", "evt-1");

            controller.handleWebhook(payload, null);

            verify(webhookPipeline).execute(eq("trigger-src"), any(), anyString());
            verify(metricsCollector).recordSource("trigger-src");
        }

        @Test
        @DisplayName("Should default to unknown when no source or trigger")
        void shouldDefaultUnknown() {
            WebhookPipeline.PipelineResult expectedResult = new WebhookPipeline.PipelineResult(
                    WebhookPipeline.PipelineResult.Status.ACCEPTED,
                    Map.of("status", "accepted"), HttpStatus.OK);
            when(webhookPipeline.execute(eq("unknown"), any(), anyString())).thenReturn(expectedResult);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", "evt-1");

            controller.handleWebhook(payload, null);

            verify(webhookPipeline).execute(eq("unknown"), any(), anyString());
            verify(metricsCollector).recordSource("unknown");
        }
    }

    @Nested
    @DisplayName("Metrics Recording")
    class MetricsTests {

        @Test
        @DisplayName("Should record webhook received and source on each request")
        void shouldRecordMetrics() {
            WebhookPipeline.PipelineResult expectedResult = new WebhookPipeline.PipelineResult(
                    WebhookPipeline.PipelineResult.Status.ACCEPTED,
                    Map.of("status", "accepted"), HttpStatus.OK);
            when(webhookPipeline.execute(eq("gitguardian"), any(), anyString())).thenReturn(expectedResult);

            Map<String, Object> payload = Map.of("source", "gitguardian", "id", "evt-1");

            controller.handleWebhook(payload, null);

            verify(metricsCollector).recordWebhookReceived();
            verify(metricsCollector).recordSource("gitguardian");
        }

        @Test
        @DisplayName("Should record failed metrics on pipeline error")
        void shouldRecordFailureMetrics() {
            when(webhookPipeline.execute(anyString(), any(), anyString()))
                    .thenThrow(new RuntimeException("Pipeline error"));

            Map<String, Object> payload = Map.of("source", "gitguardian", "id", "evt-1");

            controller.handleWebhook(payload, null);

            verify(metricsCollector).recordWebhookReceived();
            verify(metricsCollector).recordAlertFailed();
        }
    }

    @Nested
    @DisplayName("Response Pass-Through")
    class ResponseTests {

        @Test
        @DisplayName("Should pass through accepted response")
        void shouldPassThroughAccepted() {
            WebhookPipeline.PipelineResult expectedResult = new WebhookPipeline.PipelineResult(
                    WebhookPipeline.PipelineResult.Status.ACCEPTED,
                    Map.of("status", "accepted", "event_id", "evt-123", "source", "gitguardian"),
                    HttpStatus.OK);
            when(webhookPipeline.execute(eq("gitguardian"), any(), anyString())).thenReturn(expectedResult);

            Map<String, Object> payload = Map.of("source", "gitguardian", "id", "evt-123");

            ResponseEntity<?> result = controller.handleWebhook(payload, null);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("accepted", body.get("status"));
            assertEquals("evt-123", body.get("event_id"));
        }

        @Test
        @DisplayName("Should pass through 401 unauthorized response")
        void shouldPassThroughUnauthorized() {
            WebhookPipeline.PipelineResult expectedResult = new WebhookPipeline.PipelineResult(
                    WebhookPipeline.PipelineResult.Status.SIGNATURE_INVALID,
                    Map.of("error", "INVALID_SIGNATURE", "message", "Webhook signature validation failed"),
                    HttpStatus.UNAUTHORIZED);
            when(webhookPipeline.execute(eq("gitguardian"), any(), anyString())).thenReturn(expectedResult);

            Map<String, Object> payload = Map.of("source", "gitguardian", "id", "evt-1");

            ResponseEntity<?> result = controller.handleWebhook(payload, null);

            assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("INVALID_SIGNATURE", body.get("error"));
        }

        @Test
        @DisplayName("Should pass through 403 forbidden response")
        void shouldPassThroughForbidden() {
            WebhookPipeline.PipelineResult expectedResult = new WebhookPipeline.PipelineResult(
                    WebhookPipeline.PipelineResult.Status.IP_FORBIDDEN,
                    Map.of("error", "IP_FORBIDDEN", "message", "Request from untrusted IP address"),
                    HttpStatus.FORBIDDEN);
            when(webhookPipeline.execute(eq("gitguardian"), any(), anyString())).thenReturn(expectedResult);

            Map<String, Object> payload = Map.of("source", "gitguardian", "id", "evt-1");

            ResponseEntity<?> result = controller.handleWebhook(payload, null);

            assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
        }

        @Test
        @DisplayName("Should pass through 429 backpressure response")
        void shouldPassThroughBackpressure() {
            WebhookPipeline.PipelineResult expectedResult = new WebhookPipeline.PipelineResult(
                    WebhookPipeline.PipelineResult.Status.BACKPRESSURE,
                    Map.of("status", "backpressure", "message", "Server busy, alert dropped"),
                    HttpStatus.TOO_MANY_REQUESTS);
            when(webhookPipeline.execute(eq("gitguardian"), any(), anyString())).thenReturn(expectedResult);

            Map<String, Object> payload = Map.of("source", "gitguardian", "id", "evt-1");

            ResponseEntity<?> result = controller.handleWebhook(payload, null);

            assertEquals(HttpStatus.TOO_MANY_REQUESTS, result.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("backpressure", body.get("status"));
        }

        @Test
        @DisplayName("Should pass through duplicate_skipped response")
        void shouldPassThroughDuplicateSkipped() {
            WebhookPipeline.PipelineResult expectedResult = new WebhookPipeline.PipelineResult(
                    WebhookPipeline.PipelineResult.Status.DUPLICATE_SKIPPED,
                    Map.of("status", "duplicate_skipped", "source_event_id", "evt-dup"),
                    HttpStatus.OK);
            when(webhookPipeline.execute(eq("gitguardian"), any(), anyString())).thenReturn(expectedResult);

            Map<String, Object> payload = Map.of("source", "gitguardian", "id", "evt-dup");

            ResponseEntity<?> result = controller.handleWebhook(payload, null);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("duplicate_skipped", body.get("status"));
        }

        @Test
        @DisplayName("Should pass through secret_dedup_cooldown response")
        void shouldPassThroughSecretDedupCooldown() {
            WebhookPipeline.PipelineResult expectedResult = new WebhookPipeline.PipelineResult(
                    WebhookPipeline.PipelineResult.Status.SECRET_COOLDOWN,
                    Map.of("status", "secret_dedup_cooldown", "source_event_id", "evt-cooldown"),
                    HttpStatus.OK);
            when(webhookPipeline.execute(eq("gitguardian"), any(), anyString())).thenReturn(expectedResult);

            Map<String, Object> payload = Map.of(
                    "source", "gitguardian",
                    "id", "evt-cooldown",
                    "incident", Map.of("value_hash", "vh-1"));

            ResponseEntity<?> result = controller.handleWebhook(payload, null);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("secret_dedup_cooldown", body.get("status"));
        }

        @Test
        @DisplayName("Should pass through processing_failed response")
        void shouldPassThroughProcessingFailed() {
            WebhookPipeline.PipelineResult expectedResult = new WebhookPipeline.PipelineResult(
                    WebhookPipeline.PipelineResult.Status.PROCESSING_FAILED,
                    Map.of("status", "processing_failed", "message", "Alert sent to dead letter queue"),
                    HttpStatus.OK);
            when(webhookPipeline.execute(eq("gitguardian"), any(), anyString())).thenReturn(expectedResult);

            Map<String, Object> payload = Map.of(
                    "source", "gitguardian",
                    "id", "evt-error",
                    "incident", Map.of("value_hash", "vh-1"));

            ResponseEntity<?> result = controller.handleWebhook(payload, null);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("processing_failed", body.get("status"));
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorTests {

        @Test
        @DisplayName("Should return 500 on unexpected pipeline exception")
        void shouldReturn500OnUnexpectedError() {
            when(webhookPipeline.execute(eq("gitguardian"), any(), anyString()))
                    .thenThrow(new RuntimeException("Unexpected error"));

            Map<String, Object> payload = Map.of("source", "gitguardian", "id", "evt-1");

            ResponseEntity<?> result = controller.handleWebhook(payload, null);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("error", body.get("status"));
            assertEquals("Internal processing error", body.get("message"));
        }

        @Test
        @DisplayName("Should not throw on null request")
        void shouldHandleNullRequest() {
            WebhookPipeline.PipelineResult expectedResult = new WebhookPipeline.PipelineResult(
                    WebhookPipeline.PipelineResult.Status.ACCEPTED,
                    Map.of("status", "accepted"), HttpStatus.OK);
            when(webhookPipeline.execute(eq("gitguardian"), any(), anyString())).thenReturn(expectedResult);

            Map<String, Object> payload = Map.of("source", "gitguardian", "id", "evt-1");

            assertDoesNotThrow(() -> controller.handleWebhook(payload, null));
        }
    }
}
