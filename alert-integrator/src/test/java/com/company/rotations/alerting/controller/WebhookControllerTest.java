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

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebhookControllerTest {

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
    }

    private Map<String, Object> buildPayload(String source, String eventId, String valueHash) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", source);
        payload.put("id", eventId);
        payload.put("value_hash", valueHash);
        return payload;
    }

    private Map<String, Object> buildPayloadWithIncident(String source, String incidentId, String valueHash, String repo) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", source);
        Map<String, Object> incident = new LinkedHashMap<>();
        incident.put("id", incidentId);
        incident.put("value_hash", valueHash);
        incident.put("repository", repo);
        payload.put("incident", incident);
        return payload;
    }

    private Map<String, Object> buildGitGuardianPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "gitguardian");
        Map<String, Object> incident = new LinkedHashMap<>();
        incident.put("id", "gg-evt-1");
        incident.put("value_hash", "vh-123");
        incident.put("secret_type", "AWS_KEY");
        incident.put("repository", "my-repo");
        payload.put("incident", incident);
        return payload;
    }

    @Nested
    @DisplayName("Signature Validation Failure")
    class SignatureFailureTests {

        @Test
        @DisplayName("Should return 401 when signature is invalid")
        void shouldReturnUnauthorizedWhenInvalid() {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(false);
            when(signatureValidator.getSignatureHeaderName()).thenReturn("X-Signature");
            when(request.getHeader("X-Signature")).thenReturn("bad-signature");

            Map<String, Object> payload = buildPayload("gitguardian", "evt-1", "vh-1");

            ResponseEntity<?> result = controller.handleWebhook(payload, request, response);

            assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
            assertEquals("INVALID_SIGNATURE", ((Map<String, Object>) result.getBody()).get("error"));
            verify(auditService).logWebhookReceived(eq("gitguardian"), any());
            verify(auditService).logSignatureVerificationFailed(eq("gitguardian"), eq("bad-signature"));
        }

        @Test
        @DisplayName("Should return 401 when signature is missing")
        void shouldReturnUnauthorizedWhenMissing() {
            when(signatureValidator.isValid(any(), isNull(), any())).thenReturn(false);
            when(signatureValidator.getSignatureHeaderName()).thenReturn("X-Signature");
            when(request.getHeader("X-Signature")).thenReturn(null);

            Map<String, Object> payload = buildPayload("gitguardian", "evt-1", "vh-1");

            ResponseEntity<?> result = controller.handleWebhook(payload, request, response);

            assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        }
    }

    @Nested
    @DisplayName("IP Whitelist Failure")
    class IPFailureTests {

        @Test
        @DisplayName("Should return 403 when IP is not allowed")
        void shouldReturnForbiddenWhenIpBlocked() {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(signatureValidator.getSignatureHeaderName()).thenReturn("X-Signature");
            when(request.getHeader("X-Signature")).thenReturn("valid");
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(false);
            when(request.getRemoteAddr()).thenReturn("10.99.99.99");

            Map<String, Object> payload = buildPayload("gitguardian", "evt-1", "vh-1");

            ResponseEntity<?> result = controller.handleWebhook(payload, request, response);

            assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
            verify(auditService).logIpVerificationFailed(eq("gitguardian"), eq("10.99.99.99"));
        }
    }

    @Nested
    @DisplayName("Event Dedup")
    class EventDedupTests {

        @Test
        @DisplayName("Should skip duplicate events")
        void shouldSkipDuplicates() {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(signatureValidator.getSignatureHeaderName()).thenReturn("X-Signature");
            when(request.getHeader("X-Signature")).thenReturn("valid");
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(true);
            when(eventDedupService.isDuplicate("evt-dup")).thenReturn(true);

            Map<String, Object> payload = buildPayload("gitguardian", "evt-dup", "vh-1");

            ResponseEntity<?> result = controller.handleWebhook(payload, request, response);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("duplicate_skipped", body.get("status"));
            verify(auditService).logAlertDeduplicated(eq("gitguardian"), eq("evt-dup"), eq("event_dedup"));
        }

        @Test
        @DisplayName("Should extract event ID from incident nested object")
        void shouldExtractFromIncident() {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(signatureValidator.getSignatureHeaderName()).thenReturn("X-Signature");
            when(request.getHeader("X-Signature")).thenReturn("valid");
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(true);
            when(eventDedupService.isDuplicate("incident-1")).thenReturn(true);

            Map<String, Object> payload = buildPayloadWithIncident("gitguardian", "incident-1", "vh-1", "repo");

            ResponseEntity<?> result = controller.handleWebhook(payload, request, response);

            assertEquals("incident-1", ((Map<String, Object>) result.getBody()).get("source_event_id"));
        }
    }

    @Nested
    @DisplayName("Secret Dedup")
    class SecretDedupTests {

        @Test
        @DisplayName("Should skip during secret dedup cooldown")
        void shouldSkipCooldown() {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(signatureValidator.getSignatureHeaderName()).thenReturn("X-Signature");
            when(request.getHeader("X-Signature")).thenReturn("valid");
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(true);
            when(eventDedupService.isDuplicate("evt-1")).thenReturn(false);
            when(secretDedupService.checkOrRegister("vh-1", "repo"))
                    .thenReturn(SecretDedupService.DedupResult.SKIP_COOLDOWN);

            Map<String, Object> payload = buildPayloadWithIncident("gitguardian", "evt-1", "vh-1", "repo");

            ResponseEntity<?> result = controller.handleWebhook(payload, request, response);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("secret_dedup_cooldown", body.get("status"));
        }

        @Test
        @DisplayName("Should skip when secret is in progress")
        void shouldSkipInProgress() {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(signatureValidator.getSignatureHeaderName()).thenReturn("X-Signature");
            when(request.getHeader("X-Signature")).thenReturn("valid");
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(true);
            when(eventDedupService.isDuplicate("evt-1")).thenReturn(false);
            when(secretDedupService.checkOrRegister("vh-1", "repo"))
                    .thenReturn(SecretDedupService.DedupResult.SKIP_IN_PROGRESS);

            Map<String, Object> payload = buildPayloadWithIncident("gitguardian", "evt-1", "vh-1", "repo");

            ResponseEntity<?> result = controller.handleWebhook(payload, request, response);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("secret_in_progress", body.get("status"));
        }

        @Test
        @DisplayName("Should proceed when no secret dedup")
        void shouldProceedWithNoHash() {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(signatureValidator.getSignatureHeaderName()).thenReturn("X-Signature");
            when(request.getHeader("X-Signature")).thenReturn("valid");
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(true);
            when(eventDedupService.isDuplicate("evt-1")).thenReturn(false);
            when(secretDedupService.checkOrRegister(null, null))
                    .thenReturn(SecretDedupService.DedupResult.PROCEED);

            GenericAlertModel alert = new GenericAlertModel();
            alert.setEventId("alert-1");
            when(adapterRegistry.adapt(eq("gitguardian"), any())).thenReturn(alert);

            Map<String, Object> payload = buildPayload("gitguardian", "evt-1", null);

            ResponseEntity<?> result = controller.handleWebhook(payload, request, response);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("accepted", body.get("status"));
        }
    }

    @Nested
    @DisplayName("Successful Processing")
    class SuccessTests {

        @Test
        @DisplayName("Should process webhook successfully")
        void shouldProcessSuccessfully() {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(signatureValidator.getSignatureHeaderName()).thenReturn("X-Signature");
            when(request.getHeader("X-Signature")).thenReturn("valid");
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(true);
            when(eventDedupService.isDuplicate("evt-1")).thenReturn(false);
            when(secretDedupService.checkOrRegister("vh-1", "my-repo"))
                    .thenReturn(SecretDedupService.DedupResult.PROCEED);

            GenericAlertModel alert = new GenericAlertModel();
            alert.setEventId("alert-1");
            alert.setSource("gitguardian");
            GenericAlertModel.DetectedSecret secret = new GenericAlertModel.DetectedSecret();
            secret.setType("aws_key");
            alert.setDetectedSecret(secret);
            when(adapterRegistry.adapt(eq("gitguardian"), any())).thenReturn(alert);

            Map<String, Object> payload = buildPayloadWithIncident("gitguardian", "evt-1", "vh-1", "my-repo");

            ResponseEntity<?> result = controller.handleWebhook(payload, request, response);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("accepted", body.get("status"));
            assertEquals("alert-1", body.get("event_id"));
            verify(auditService).logAlertIngested(eq("gitguardian"), any(GenericAlertModel.class), anyString());
            verify(dlqService).processAlert(any(WebhookPayload.class));
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorTests {

        @Test
        @DisplayName("Should handle pipeline error gracefully")
        void shouldHandlePipelineError() {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(signatureValidator.getSignatureHeaderName()).thenReturn("X-Signature");
            when(request.getHeader("X-Signature")).thenReturn("valid");
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(true);
            when(eventDedupService.isDuplicate("evt-1")).thenReturn(false);
            when(secretDedupService.checkOrRegister("vh-1", "repo"))
                    .thenReturn(SecretDedupService.DedupResult.PROCEED);
            when(adapterRegistry.adapt(eq("gitguardian"), any()))
                    .thenThrow(new RuntimeException("Adapter failed"));

            Map<String, Object> payload = buildPayloadWithIncident("gitguardian", "evt-1", "vh-1", "repo");

            ResponseEntity<?> result = controller.handleWebhook(payload, request, response);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            Map<String, Object> body = (Map<String, Object>) result.getBody();
            assertEquals("processing_failed", body.get("status"));
            verify(auditService).logProcessingFailed(eq("gitguardian"), anyMap(), eq("Adapter failed"));
            verify(dlqService).addToDLQ(any(Exception.class), anyMap(), eq("gitguardian"), eq("evt-1"), eq("pipeline_error"));
        }

        @Test
        @DisplayName("Should handle DLQ write failure during error")
        void shouldHandleDlqWriteFailure() {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(signatureValidator.getSignatureHeaderName()).thenReturn("X-Signature");
            when(request.getHeader("X-Signature")).thenReturn("valid");
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(true);
            when(eventDedupService.isDuplicate("evt-1")).thenReturn(false);
            when(secretDedupService.checkOrRegister("vh-1", "repo"))
                    .thenReturn(SecretDedupService.DedupResult.PROCEED);
            when(adapterRegistry.adapt(eq("gitguardian"), any()))
                    .thenThrow(new RuntimeException("Crash"));
            doThrow(new RuntimeException("DLQ full"))
                    .when(dlqService).addToDLQ(any(), any(), any(), any(), any());

            Map<String, Object> payload = buildPayloadWithIncident("gitguardian", "evt-1", "vh-1", "repo");

            // Should not throw
            assertDoesNotThrow(() -> controller.handleWebhook(payload, request, response));
        }
    }

    @Nested
    @DisplayName("Source Detection")
    class SourceTests {

        @Test
        @DisplayName("Should detect source from source field")
        void shouldDetectFromSource() {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(signatureValidator.getSignatureHeaderName()).thenReturn("X-Signature");
            when(request.getHeader("X-Signature")).thenReturn("valid");
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(true);
            when(eventDedupService.isDuplicate("evt-1")).thenReturn(false);
            when(secretDedupService.checkOrRegister("vh-1", "repo"))
                    .thenReturn(SecretDedupService.DedupResult.PROCEED);

            GenericAlertModel alert = new GenericAlertModel();
            alert.setEventId("alert-1");
            when(adapterRegistry.adapt(eq("my-source"), any())).thenReturn(alert);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("source", "my-source");
            payload.put("id", "evt-1");
            payload.put("value_hash", "vh-1");

            controller.handleWebhook(payload, request, response);
            verify(adapterRegistry).adapt(eq("my-source"), any());
        }

        @Test
        @DisplayName("Should detect source from trigger field when source missing")
        void shouldDetectFromTrigger() {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(signatureValidator.getSignatureHeaderName()).thenReturn("X-Signature");
            when(request.getHeader("X-Signature")).thenReturn("valid");
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(true);
            when(eventDedupService.isDuplicate("evt-1")).thenReturn(false);
            when(secretDedupService.checkOrRegister("vh-1", "repo"))
                    .thenReturn(SecretDedupService.DedupResult.PROCEED);

            GenericAlertModel alert = new GenericAlertModel();
            alert.setEventId("alert-1");
            when(adapterRegistry.adapt(eq("trigger-src"), any())).thenReturn(alert);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("trigger", "trigger-src");
            payload.put("id", "evt-1");
            payload.put("value_hash", "vh-1");

            controller.handleWebhook(payload, request, response);
            verify(adapterRegistry).adapt(eq("trigger-src"), any());
        }

        @Test
        @DisplayName("Should default to unknown when no source")
        void shouldDefaultUnknown() {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(signatureValidator.getSignatureHeaderName()).thenReturn("X-Signature");
            when(request.getHeader("X-Signature")).thenReturn("valid");
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(true);
            when(eventDedupService.isDuplicate("evt-1")).thenReturn(false);
            when(secretDedupService.checkOrRegister("vh-1", "repo"))
                    .thenReturn(SecretDedupService.DedupResult.PROCEED);

            GenericAlertModel alert = new GenericAlertModel();
            alert.setEventId("alert-1");
            when(adapterRegistry.adapt(eq("unknown"), any())).thenReturn(alert);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", "evt-1");
            payload.put("value_hash", "vh-1");

            controller.handleWebhook(payload, request, response);
            verify(adapterRegistry).adapt(eq("unknown"), any());
        }
    }

    @Nested
    @DisplayName("Value Hash Extraction")
    class ValueHashTests {

        @Test
        @DisplayName("Should extract value_hash from payload")
        void shouldExtractFromPayload() {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(signatureValidator.getSignatureHeaderName()).thenReturn("X-Signature");
            when(request.getHeader("X-Signature")).thenReturn("valid");
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(true);
            when(eventDedupService.isDuplicate("evt-1")).thenReturn(false);
            when(secretDedupService.checkOrRegister("payload-hash", null))
                    .thenReturn(SecretDedupService.DedupResult.PROCEED);

            GenericAlertModel alert = new GenericAlertModel();
            alert.setEventId("alert-1");
            when(adapterRegistry.adapt(eq("gitguardian"), any())).thenReturn(alert);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("source", "gitguardian");
            payload.put("id", "evt-1");
            payload.put("value_hash", "payload-hash");

            controller.handleWebhook(payload, request, response);
        }

        @Test
        @DisplayName("Should extract value_hash from incident")
        void shouldExtractFromIncident() {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(signatureValidator.getSignatureHeaderName()).thenReturn("X-Signature");
            when(request.getHeader("X-Signature")).thenReturn("valid");
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(true);
            when(eventDedupService.isDuplicate("evt-1")).thenReturn(false);
            when(secretDedupService.checkOrRegister("incident-hash", "repo"))
                    .thenReturn(SecretDedupService.DedupResult.PROCEED);

            GenericAlertModel alert = new GenericAlertModel();
            alert.setEventId("alert-1");
            when(adapterRegistry.adapt(eq("gitguardian"), any())).thenReturn(alert);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("source", "gitguardian");
            payload.put("id", "evt-1");
            Map<String, Object> incident = new LinkedHashMap<>();
            incident.put("value_hash", "incident-hash");
            incident.put("repository", "repo");
            payload.put("incident", incident);

            controller.handleWebhook(payload, request, response);
        }
    }

    @Nested
    @DisplayName("Repository Extraction")
    class RepositoryTests {

        @Test
        @DisplayName("Should extract repository from incident")
        void shouldExtractFromIncident() {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(signatureValidator.getSignatureHeaderName()).thenReturn("X-Signature");
            when(request.getHeader("X-Signature")).thenReturn("valid");
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(true);
            when(eventDedupService.isDuplicate("evt-1")).thenReturn(false);
            when(secretDedupService.checkOrRegister("vh-1", "my-repo"))
                    .thenReturn(SecretDedupService.DedupResult.PROCEED);

            GenericAlertModel alert = new GenericAlertModel();
            alert.setEventId("alert-1");
            when(adapterRegistry.adapt(eq("gitguardian"), any())).thenReturn(alert);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("source", "gitguardian");
            payload.put("id", "evt-1");
            Map<String, Object> incident = new LinkedHashMap<>();
            incident.put("id", "evt-1");
            incident.put("repository", "my-repo");
            payload.put("incident", incident);

            controller.handleWebhook(payload, request, response);
        }

        @Test
        @DisplayName("Should extract repo_url from incident when repository missing")
        void shouldExtractRepoUrl() {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(signatureValidator.getSignatureHeaderName()).thenReturn("X-Signature");
            when(request.getHeader("X-Signature")).thenReturn("valid");
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(true);
            when(eventDedupService.isDuplicate("evt-1")).thenReturn(false);
            when(secretDedupService.checkOrRegister("vh-1", "https://github.com/x/y"))
                    .thenReturn(SecretDedupService.DedupResult.PROCEED);

            GenericAlertModel alert = new GenericAlertModel();
            alert.setEventId("alert-1");
            when(adapterRegistry.adapt(eq("gitguardian"), any())).thenReturn(alert);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("source", "gitguardian");
            Map<String, Object> incident = new LinkedHashMap<>();
            incident.put("repo_url", "https://github.com/x/y");
            payload.put("incident", incident);

            controller.handleWebhook(payload, request, response);
        }
    }

    @Nested
    @DisplayName("Alert Processing")
    class AlertProcessingTests {

        @Test
        @DisplayName("Should log alert ingested with secret type")
        void shouldLogAlertIngested() {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(signatureValidator.getSignatureHeaderName()).thenReturn("X-Signature");
            when(request.getHeader("X-Signature")).thenReturn("valid");
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(true);
            when(eventDedupService.isDuplicate("evt-1")).thenReturn(false);
            when(secretDedupService.checkOrRegister("vh-1", "repo"))
                    .thenReturn(SecretDedupService.DedupResult.PROCEED);

            GenericAlertModel alert = new GenericAlertModel();
            alert.setEventId("alert-1");
            GenericAlertModel.DetectedSecret secret = new GenericAlertModel.DetectedSecret();
            secret.setType("aws_access_key");
            alert.setDetectedSecret(secret);
            GenericAlertModel.AlertContext ctx = new GenericAlertModel.AlertContext();
            ctx.setRepository("my-repo");
            alert.setContext(ctx);
            when(adapterRegistry.adapt(eq("gitguardian"), any())).thenReturn(alert);

            Map<String, Object> payload = buildPayloadWithIncident("gitguardian", "evt-1", "vh-1", "repo");

            controller.handleWebhook(payload, request, response);

            verify(auditService).logAlertIngested(eq("gitguardian"), any(GenericAlertModel.class), anyString());
        }

        @Test
        @DisplayName("Should handle alert with no secret")
        void shouldHandleAlertWithNoSecret() {
            when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);
            when(signatureValidator.getSignatureHeaderName()).thenReturn("X-Signature");
            when(request.getHeader("X-Signature")).thenReturn("valid");
            when(ipWhitelistValidator.isAllowed(any(), any())).thenReturn(true);
            when(eventDedupService.isDuplicate("evt-1")).thenReturn(false);
            when(secretDedupService.checkOrRegister(null, null))
                    .thenReturn(SecretDedupService.DedupResult.PROCEED);

            GenericAlertModel alert = new GenericAlertModel();
            alert.setEventId("alert-no-secret");
            when(adapterRegistry.adapt(eq("src"), any())).thenReturn(alert);

            Map<String, Object> payload = buildPayload("src", "evt-1", null);

            ResponseEntity<?> result = controller.handleWebhook(payload, request, response);

            assertEquals(HttpStatus.OK, result.getStatusCode());
        }
    }
}
