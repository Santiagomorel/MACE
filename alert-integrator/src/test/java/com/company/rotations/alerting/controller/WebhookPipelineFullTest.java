package com.company.rotations.alerting.controller;

import com.company.rotations.alerting.AlertMetricsCollector;
import com.company.rotations.alerting.adapter.AdapterRegistry;
import com.company.rotations.alerting.dedup.EventDedupService;
import com.company.rotations.alerting.dedup.SecretDedupService;
import com.company.rotations.alerting.dlq.DeadLetterQueueService;
import com.company.rotations.alerting.model.WebhookPayload;
import com.company.rotations.logging.service.AuditService;
import com.company.rotations.models.GenericAlertModel;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Full pipeline integration test using direct constructor injection.
 * Tests the complete flow: webhook -> auth -> dedup -> adapter -> DLQ
 * Verifies metrics are recorded at each stage. (Task 6.5)
 */
class WebhookPipelineFullTest {

    private SignatureValidator signatureValidator;
    private IpWhitelistValidator ipWhitelistValidator;
    private AdapterRegistry adapterRegistry;
    private EventDedupService eventDedupService;
    private SecretDedupService secretDedupService;
    private DeadLetterQueueService dlqService;
    private AuditService auditService;
    private AlertMetricsCollector metricsCollector;
    private WebhookController controller;
    private HttpServletRequest request;
    private HttpServletResponse response;

    private GenericAlertModel mockAlert;

    @BeforeEach
    void setUp() {
        signatureValidator = mock(SignatureValidator.class);
        ipWhitelistValidator = mock(IpWhitelistValidator.class);
        adapterRegistry = mock(AdapterRegistry.class);
        eventDedupService = mock(EventDedupService.class);
        secretDedupService = mock(SecretDedupService.class);
        dlqService = mock(DeadLetterQueueService.class);
        auditService = mock(AuditService.class);
        metricsCollector = mock(AlertMetricsCollector.class);
        controller = new WebhookController(
                signatureValidator, ipWhitelistValidator, adapterRegistry,
                eventDedupService, secretDedupService, dlqService, auditService, metricsCollector);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);

        mockAlert = mock(GenericAlertModel.class);
        when(mockAlert.getEventId()).thenReturn("pipeline-test-id");
        when(mockAlert.getSource()).thenReturn("gitguardian");
        when(mockAlert.getSourceEventId()).thenReturn("gg-pipeline");
        when(mockAlert.getDetectedSecret()).thenReturn(null);
        when(mockAlert.getContext()).thenReturn(null);
    }

    @Nested
    @DisplayName("Full Pipeline - Happy Path Integration")
    class FullPipelineHappyPathTests {

        @Test
        @DisplayName("Should execute complete pipeline: auth -> dedup -> adapter -> DLQ with metrics")
        void shouldExecuteCompletePipeline() throws Exception {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(true);
            when(eventDedupService.isDuplicate(any())).thenReturn(false);
            when(secretDedupService.checkOrRegister(any(), any())).thenReturn(SecretDedupService.DedupResult.PROCEED);
            when(adapterRegistry.adapt(any(), any())).thenReturn(mockAlert);
            when(adapterRegistry.getProviderName(any())).thenReturn("gitguardian");

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

            ResponseEntity<?> result = controller.handleWebhook(payload, request, response);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("accepted", body.get("status"));
            assertEquals("gitguardian", body.get("source"));

            // Verify pipeline execution order
            verify(signatureValidator).isValid(anyString(), any(), eq("gitguardian"));
            verify(ipWhitelistValidator).isAllowed(any(), eq("gitguardian"));
            verify(eventDedupService).isDuplicate("evt-123");
            verify(secretDedupService).checkOrRegister("sha256abc", "my/repo");
            verify(adapterRegistry).adapt(eq("gitguardian"), any());
            verify(dlqService).processAlert(any(WebhookPayload.class));

            // Verify audit logging
            verify(auditService).logWebhookReceived(eq("gitguardian"), any());
            verify(auditService).logAlertIngested(eq("gitguardian"), eq(mockAlert), anyString());

            // Verify metrics recording
            verify(metricsCollector).recordWebhookReceived();
            verify(metricsCollector).recordSource("gitguardian");
            verify(metricsCollector).recordAdapterRoute("gitguardian");
            verify(metricsCollector).recordAlertProcessed();
            verify(metricsCollector).recordPipelineDuration(anyLong());
        }
    }

    @Nested
    @DisplayName("Full Pipeline - Auth Failure Integration")
    class FullPipelineAuthFailureTests {

        @Test
        @DisplayName("Should reject on signature failure with metrics")
        void shouldRejectOnSignatureFailure() throws Exception {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(false);

            Map<String, Object> payload = Map.of("source", "gitguardian", "id", "evt-1");

            ResponseEntity<?> result = controller.handleWebhook(payload, request, response);

            assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("INVALID_SIGNATURE", body.get("error"));

            // Should NOT proceed to dedup or adapter
            verifyNoInteractions(eventDedupService, secretDedupService, adapterRegistry, dlqService);

            // Should record metrics
            verify(metricsCollector).recordWebhookReceived();
            verify(metricsCollector).recordWebhookRejected();
        }

        @Test
        @DisplayName("Should reject on IP blocked with proper pipeline short-circuit")
        void shouldRejectOnIpBlocked() throws Exception {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(false);

            Map<String, Object> payload = Map.of("source", "gitguardian", "id", "evt-2");

            ResponseEntity<?> result = controller.handleWebhook(payload, request, response);

            assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());

            // Should NOT proceed to dedup or adapter
            verifyNoInteractions(eventDedupService, secretDedupService, adapterRegistry, dlqService);
        }
    }

    @Nested
    @DisplayName("Full Pipeline - Dedup Integration")
    class FullPipelineDedupTests {

        @Test
        @DisplayName("Should short-circuit on event dedup hit")
        void shouldShortCircuitOnEventDedup() throws Exception {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(true);
            when(eventDedupService.isDuplicate("dup-event")).thenReturn(true);

            Map<String, Object> payload = Map.of("source", "gitguardian", "id", "dup-event");

            ResponseEntity<?> result = controller.handleWebhook(payload, request, response);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("duplicate_skipped", body.get("status"));

            // Should NOT proceed to secret dedup, adapter, or DLQ
            verify(secretDedupService, never()).checkOrRegister(any(), any());
            verify(adapterRegistry, never()).adapt(any(), any());
            verify(dlqService, never()).processAlert(any());

            verify(metricsCollector).recordEventDedupHit();
        }

        @Test
        @DisplayName("Should short-circuit on secret dedup cooldown")
        void shouldShortCircuitOnSecretDedupCooldown() throws Exception {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(true);
            when(eventDedupService.isDuplicate(any())).thenReturn(false);
            when(secretDedupService.checkOrRegister(any(), any())).thenReturn(SecretDedupService.DedupResult.SKIP_COOLDOWN);

            Map<String, Object> payload = Map.of(
                    "source", "gitguardian",
                    "id", "evt-cooldown",
                    "incident", Map.of("value_hash", "h1", "repository", "r1")
            );

            ResponseEntity<?> result = controller.handleWebhook(payload, request, response);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("secret_dedup_cooldown", body.get("status"));

            verify(adapterRegistry, never()).adapt(any(), any());
            verify(dlqService, never()).processAlert(any());
            verify(metricsCollector).recordSecretDedupCooldown();
        }

        @Test
        @DisplayName("Should short-circuit on secret dedup in progress")
        void shouldShortCircuitOnSecretDedupInProgress() throws Exception {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(true);
            when(eventDedupService.isDuplicate(any())).thenReturn(false);
            when(secretDedupService.checkOrRegister(any(), any())).thenReturn(SecretDedupService.DedupResult.SKIP_IN_PROGRESS);

            Map<String, Object> payload = Map.of(
                    "source", "gitguardian",
                    "id", "evt-progress",
                    "incident", Map.of("value_hash", "h2")
            );

            ResponseEntity<?> result = controller.handleWebhook(payload, request, response);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("secret_in_progress", body.get("status"));

            verify(adapterRegistry, never()).adapt(any(), any());
            verify(dlqService, never()).processAlert(any());
            verify(metricsCollector).recordSecretDedupInProgress();
        }
    }

    @Nested
    @DisplayName("Full Pipeline - Error Handling Integration")
    class FullPipelineErrorTests {

        @Test
        @DisplayName("Should send to DLQ when adapter fails and record failure metrics")
        void shouldSendToDlqOnAdapterFailure() throws Exception {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(true);
            when(eventDedupService.isDuplicate(any())).thenReturn(false);
            when(secretDedupService.checkOrRegister(any(), any())).thenReturn(SecretDedupService.DedupResult.PROCEED);
            doThrow(new RuntimeException("Adapter not found"))
                    .when(adapterRegistry).adapt(any(), any());

            Map<String, Object> payload = Map.of(
                    "source", "gitguardian",
                    "id", "evt-error",
                    "incident", Map.of("value_hash", "h3")
            );

            ResponseEntity<?> result = controller.handleWebhook(payload, request, response);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("processing_failed", body.get("status"));

            verify(dlqService).addToDLQ(any(), any(), eq("gitguardian"), eq("evt-error"), eq("pipeline_error"));
            verify(metricsCollector).recordAlertFailed();
            verify(metricsCollector).recordAlertToDlq();
        }
    }
}
