package com.company.rotations.alerting.pipeline;

import com.company.rotations.alerting.AlertMetricsCollector;
import com.company.rotations.alerting.adapter.AdapterRegistry;
import com.company.rotations.alerting.controller.IpWhitelistValidator;
import com.company.rotations.alerting.controller.SignatureValidator;
import com.company.rotations.alerting.dedup.EventDedupService;
import com.company.rotations.alerting.dedup.SecretDedupService;
import com.company.rotations.alerting.dlq.DeadLetterQueueService;
import com.company.rotations.alerting.model.WebhookPayload;
import com.company.rotations.alerting.worker.WorkerPool;
import com.company.rotations.logging.service.AuditService;
import com.company.rotations.models.GenericAlertModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class WebhookPipeline {

    private static final Logger logger = LoggerFactory.getLogger(WebhookPipeline.class);

    private final SignatureValidator signatureValidator;
    private final IpWhitelistValidator ipWhitelistValidator;
    private final EventDedupService eventDedupService;
    private final SecretDedupService secretDedupService;
    private final AdapterRegistry adapterRegistry;
    private final WorkerPool workerPool;
    private final DeadLetterQueueService dlqService;
    private final AuditService auditService;
    private final AlertMetricsCollector metricsCollector;

    public WebhookPipeline(SignatureValidator signatureValidator,
                           IpWhitelistValidator ipWhitelistValidator,
                           EventDedupService eventDedupService,
                           SecretDedupService secretDedupService,
                           AdapterRegistry adapterRegistry,
                           WorkerPool workerPool,
                           DeadLetterQueueService dlqService,
                           AuditService auditService,
                           AlertMetricsCollector metricsCollector) {
        this.signatureValidator = signatureValidator;
        this.ipWhitelistValidator = ipWhitelistValidator;
        this.eventDedupService = eventDedupService;
        this.secretDedupService = secretDedupService;
        this.adapterRegistry = adapterRegistry;
        this.workerPool = workerPool;
        this.dlqService = dlqService;
        this.auditService = auditService;
        this.metricsCollector = metricsCollector;
    }

    /**
     * Pipeline result encapsulating outcome and optional HTTP status.
     */
    public record PipelineResult(Status status, Object body, HttpStatus httpStatus) {
        public enum Status {
            ACCEPTED, DUPLICATE_SKIPPED, SECRET_COOLDOWN, SECRET_IN_PROGRESS,
            SIGNATURE_INVALID, IP_FORBIDDEN, PROCESSING_FAILED, BACKPRESSURE
        }
    }

    public PipelineResult execute(String source, Map<String, Object> rawPayload, String requestId) {
        String body = convertToString(rawPayload);

        // Step 1: Signature validation
        if (!signatureValidator.isValid(body, getSignatureHeader(), source)) {
            logger.warn("[{}] Signature validation failed for source: {}", requestId, source);
            metricsCollector.recordWebhookRejected();
            auditService.logSignatureVerificationFailed(source, getSignatureHeader());
            return new PipelineResult(
                    PipelineResult.Status.SIGNATURE_INVALID,
                    Map.of("error", "INVALID_SIGNATURE", "message", "Webhook signature validation failed", "request_id", requestId),
                    HttpStatus.UNAUTHORIZED);
        }

        // Step 2: IP whitelist validation
        if (!ipWhitelistValidator.isAllowed(null, source)) {
            logger.warn("[{}] IP blocked for source: {} from IP: {}", requestId, source, "unknown");
            auditService.logIpVerificationFailed(source, "unknown");
            return new PipelineResult(
                    PipelineResult.Status.IP_FORBIDDEN,
                    Map.of("error", "IP_FORBIDDEN", "message", "Request from untrusted IP address", "request_id", requestId),
                    HttpStatus.FORBIDDEN);
        }

        // Step 3: Event deduplication
        String sourceEventId = extractSourceEventId(rawPayload);
        if (sourceEventId != null && eventDedupService.isDuplicate(sourceEventId)) {
            logger.info("[{}] Duplicate event skipped: sourceEventId={}", requestId, sourceEventId);
            metricsCollector.recordEventDedupHit();
            auditService.logAlertDeduplicated(source, sourceEventId, "event_dedup");
            return new PipelineResult(
                    PipelineResult.Status.DUPLICATE_SKIPPED,
                    Map.of("status", "duplicate_skipped", "source_event_id", sourceEventId, "request_id", requestId),
                    HttpStatus.OK);
        }

        // Step 4: Secret deduplication
        String valueHash = extractValueHash(rawPayload);
        String repository = extractRepository(rawPayload);
        SecretDedupService.DedupResult secretDedupResult = secretDedupService.checkOrRegister(valueHash, repository);

        if (secretDedupResult == SecretDedupService.DedupResult.SKIP_COOLDOWN) {
            logger.info("[{}] Secret dedup cooldown skip: sourceEventId={}", requestId, sourceEventId);
            metricsCollector.recordSecretDedupCooldown();
            auditService.logAlertDeduplicated(source, sourceEventId, "secret_dedup_cooldown");
            return new PipelineResult(
                    PipelineResult.Status.SECRET_COOLDOWN,
                    Map.of("status", "secret_dedup_cooldown", "source_event_id", sourceEventId, "request_id", requestId),
                    HttpStatus.OK);
        }

        if (secretDedupResult == SecretDedupService.DedupResult.SKIP_IN_PROGRESS) {
            logger.info("[{}] Secret already being processed: sourceEventId={}", requestId, sourceEventId);
            metricsCollector.recordSecretDedupInProgress();
            auditService.logAlertDeduplicated(source, sourceEventId, "secret_dedup_in_progress");
            return new PipelineResult(
                    PipelineResult.Status.SECRET_IN_PROGRESS,
                    Map.of("status", "secret_in_progress", "source_event_id", sourceEventId, "request_id", requestId),
                    HttpStatus.OK);
        }

        // Step 5: Adapt alert via registry
        String adapterName = adapterRegistry.getProviderName(source);
        metricsCollector.recordAdapterRoute(adapterName);
        GenericAlertModel genericAlert = adapterRegistry.adapt(source, rawPayload);

        // Step 6: Submit to worker pool
        WebhookPayload payload = new WebhookPayload(genericAlert, body, source, Instant.now());

        try {
            dlqService.processAlert(payload);
            boolean submitted = workerPool.submit(payload);

            if (!submitted) {
                logger.warn("[{}] Backpressure: queue full, alert not queued for source={}", requestId, source);
                metricsCollector.recordAlertFailed();
                return new PipelineResult(
                        PipelineResult.Status.BACKPRESSURE,
                        Map.of("status", "backpressure", "message", "Server busy, alert dropped", "request_id", requestId),
                        HttpStatus.TOO_MANY_REQUESTS);
            }

            metricsCollector.recordAlertProcessed();

            logger.info("[{}] Alert processed successfully: source={}, eventId={}, secretType={}, backpressure={}",
                    requestId, source, genericAlert.getEventId(),
                    genericAlert.getDetectedSecret() != null ? genericAlert.getDetectedSecret().getType() : "unknown",
                    false);

            auditService.logAlertIngested(source, genericAlert, requestId);

            return new PipelineResult(
                    PipelineResult.Status.ACCEPTED,
                    Map.of("status", "accepted", "event_id", genericAlert.getEventId(), "source", source, "request_id", requestId),
                    HttpStatus.OK);

        } catch (Exception e) {
            metricsCollector.recordAlertFailed();
            metricsCollector.recordAlertToDlq();

            logger.error("[{}] Pipeline error: {}", requestId, e.getMessage(), e);
            auditService.logProcessingFailed(source, rawPayload, e.getMessage());

            try {
                dlqService.addToDLQ(e, rawPayload, source, extractSourceEventId(rawPayload), "pipeline_error");
            } catch (Exception dlqEx) {
                logger.error("[{}] Failed to write to DLQ: {}", requestId, dlqEx.getMessage());
            }

            return new PipelineResult(
                    PipelineResult.Status.PROCESSING_FAILED,
                    Map.of("status", "processing_failed", "message", "Alert sent to dead letter queue", "request_id", requestId),
                    HttpStatus.OK);
        }
    }

    private String getSignatureHeader() {
        return signatureValidator.getSignatureHeaderName();
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
