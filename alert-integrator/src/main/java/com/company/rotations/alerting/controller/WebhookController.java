package com.company.rotations.alerting.controller;

import com.company.rotations.alerting.adapter.AdapterRegistry;
import com.company.rotations.alerting.dedup.EventDedupService;
import com.company.rotations.alerting.dedup.SecretDedupService;
import com.company.rotations.alerting.dlq.AlertDLQEntry;
import com.company.rotations.alerting.dlq.DeadLetterQueueService;
import com.company.rotations.alerting.model.WebhookPayload;
import com.company.rotations.logging.service.AuditService;
import com.company.rotations.models.GenericAlertModel;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("${app.alerting.webhook.path:/api/v1/alerts}")
@Validated
public class WebhookController {

    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);

    private final SignatureValidator signatureValidator;
    private final IpWhitelistValidator ipWhitelistValidator;
    private final AdapterRegistry adapterRegistry;
    private final EventDedupService eventDedupService;
    private final SecretDedupService secretDedupService;
    private final DeadLetterQueueService dlqService;
    private final AuditService auditService;

    public WebhookController(SignatureValidator signatureValidator,
                             IpWhitelistValidator ipWhitelistValidator,
                             AdapterRegistry adapterRegistry,
                             EventDedupService eventDedupService,
                             SecretDedupService secretDedupService,
                             DeadLetterQueueService dlqService,
                             AuditService auditService) {
        this.signatureValidator = signatureValidator;
        this.ipWhitelistValidator = ipWhitelistValidator;
        this.adapterRegistry = adapterRegistry;
        this.eventDedupService = eventDedupService;
        this.secretDedupService = secretDedupService;
        this.dlqService = dlqService;
        this.auditService = auditService;
    }

    @PostMapping
    public ResponseEntity<?> handleWebhook(
            @RequestBody Map<String, Object> rawPayload,
            HttpServletRequest request,
            HttpServletResponse response) {

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", requestId);

        String source = detectSource(rawPayload);
        long startTime = System.currentTimeMillis();

        try {
            logger.info("[{}] Webhook received from source: {}", requestId, source);
            auditService.logWebhookReceived(source, rawPayload);

            String body = convertToString(rawPayload);

            if (!signatureValidator.isValid(body, getSignature(request), source)) {
                logger.warn("[{}] Signature validation failed for source: {}", requestId, source);
                auditService.logSignatureVerificationFailed(source, getSignature(request));
                MDC.remove("requestId");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of(
                                "error", "INVALID_SIGNATURE",
                                "message", "Webhook signature validation failed",
                                "request_id", requestId
                        ));
            }

            if (!ipWhitelistValidator.isAllowed(request, source)) {
                logger.warn("[{}] IP blocked for source: {} from IP: {}",
                        requestId, source, request.getRemoteAddr());
                auditService.logIpVerificationFailed(source, request.getRemoteAddr());
                MDC.remove("requestId");
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                                "error", "IP_FORBIDDEN",
                                "message", "Request from untrusted IP address",
                                "request_id", requestId
                        ));
            }

            String sourceEventId = extractSourceEventId(rawPayload);
            if (sourceEventId != null && eventDedupService.isDuplicate(sourceEventId)) {
                logger.info("[{}] Duplicate event skipped: sourceEventId={}", requestId, sourceEventId);
                auditService.logAlertDeduplicated(source, sourceEventId, "event_dedup");
                MDC.remove("requestId");
                return ResponseEntity.status(HttpStatus.OK)
                        .body(Map.of(
                                "status", "duplicate_skipped",
                                "source_event_id", sourceEventId,
                                "request_id", requestId
                        ));
            }

            String valueHash = extractValueHash(rawPayload);
            SecretDedupService.DedupResult secretDedupResult = secretDedupService.checkOrRegister(
                    valueHash, extractRepository(rawPayload));

            if (secretDedupResult == SecretDedupService.DedupResult.SKIP_COOLDOWN) {
                logger.info("[{}] Secret dedup cooldown skip: sourceEventId={}", requestId, sourceEventId);
                auditService.logAlertDeduplicated(source, sourceEventId, "secret_dedup_cooldown");
                MDC.remove("requestId");
                return ResponseEntity.status(HttpStatus.OK)
                        .body(Map.of(
                                "status", "secret_dedup_cooldown",
                                "source_event_id", sourceEventId,
                                "request_id", requestId
                        ));
            }

            if (secretDedupResult == SecretDedupService.DedupResult.SKIP_IN_PROGRESS) {
                logger.info("[{}] Secret already being processed: sourceEventId={}", requestId, sourceEventId);
                auditService.logAlertDeduplicated(source, sourceEventId, "secret_dedup_in_progress");
                MDC.remove("requestId");
                return ResponseEntity.status(HttpStatus.OK)
                        .body(Map.of(
                                "status", "secret_in_progress",
                                "source_event_id", sourceEventId,
                                "request_id", requestId
                        ));
            }

            GenericAlertModel genericAlert = adapterRegistry.adapt(source, rawPayload);

            WebhookPayload payload = new WebhookPayload(genericAlert, body, source, Instant.now());

            logger.info("[{}] Alert processed successfully: source={}, eventId={}, secretType={}",
                    requestId, source, genericAlert.getEventId(),
                    genericAlert.getDetectedSecret() != null ? genericAlert.getDetectedSecret().getType() : "unknown");

            auditService.logAlertIngested(source, genericAlert, requestId);

            dlqService.processAlert(payload);

            long duration = System.currentTimeMillis() - startTime;
            MDC.put("durationMs", String.valueOf(duration));
            return ResponseEntity.ok(Map.of(
                    "status", "accepted",
                    "event_id", genericAlert.getEventId(),
                    "source", source,
                    "request_id", requestId
            ));

        } catch (Exception e) {
            logger.error("[{}] Pipeline error: {}", requestId, e.getMessage(), e);
            auditService.logProcessingFailed(source, rawPayload, e.getMessage());

            try {
                dlqService.addToDLQ(e, rawPayload, source, extractSourceEventId(rawPayload), "pipeline_error");
            } catch (Exception dlqEx) {
                logger.error("[{}] Failed to write to DLQ: {}", requestId, dlqEx.getMessage());
            }

            MDC.remove("requestId");
            return ResponseEntity.status(HttpStatus.OK)
                    .body(Map.of(
                            "status", "processing_failed",
                            "message", "Alert sent to dead letter queue",
                            "request_id", requestId
                    ));
        } finally {
            MDC.remove("requestId");
        }
    }

    private String detectSource(Map<String, Object> payload) {
        Object source = payload.get("source");
        if (source != null) return source.toString();
        Object trigger = payload.get("trigger");
        if (trigger != null) return trigger.toString();
        return "unknown";
    }

    private String getSignature(HttpServletRequest request) {
        String header = signatureValidator.getSignatureHeaderName();
        return request.getHeader(header);
    }

    private String extractSourceEventId(Map<String, Object> payload) {
        Object id = payload.get("id");
        if (id != null) return id.toString();
        Map<String, Object> incident = (Map<String, Object>) payload.get("incident");
        if (incident != null) {
            Object incidentId = incident.get("id");
            if (incidentId != null) return incidentId.toString();
        }
        return null;
    }

    private String extractValueHash(Map<String, Object> payload) {
        Map<String, Object> incident = (Map<String, Object>) payload.get("incident");
        if (incident != null) {
            Object hash = incident.get("value_hash");
            if (hash != null) return hash.toString();
        }
        Object hash = payload.get("value_hash");
        return hash != null ? hash.toString() : null;
    }

    private String extractRepository(Map<String, Object> payload) {
        Map<String, Object> incident = (Map<String, Object>) payload.get("incident");
        if (incident != null) {
            Object repo = incident.get("repository");
            if (repo != null) return repo.toString();
            repo = incident.get("repo_url");
            if (repo != null) return repo.toString();
        }
        Object repo = payload.get("repository");
        return repo != null ? repo.toString() : null;
    }

    private String convertToString(Map<String, Object> payload) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(payload);
        } catch (Exception e) {
            return payload.toString();
        }
    }
}
