package com.company.rotations.alerting.dlq;

import com.company.rotations.alerting.model.WebhookPayload;
import com.company.rotations.logging.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

@Service
public class DeadLetterQueueService {

    private static final Logger logger = LoggerFactory.getLogger(DeadLetterQueueService.class);

    private final AlertDLQRepository dlqRepository;
    private final AuditService auditService;
    private final int maxRetries;
    private final long retentionDays;

    public DeadLetterQueueService(AlertDLQRepository dlqRepository,
                                  AuditService auditService,
                                  @Value("${app.dlq.max-retries:3}") int maxRetries,
                                  @Value("${app.dlq.retention-days:7}") long retentionDays) {
        this.dlqRepository = dlqRepository;
        this.auditService = auditService;
        this.maxRetries = maxRetries;
        this.retentionDays = retentionDays;
    }

    public void processAlert(WebhookPayload payload) {
        try {
            auditService.logProcessingStarted(payload.source(), payload.alert().getEventId());
            logger.info("Alert queued for processing: eventId={}, source={}",
                    payload.alert().getEventId(), payload.source());
        } catch (Exception e) {
            logger.warn("Could not log processing started: {}", e.getMessage());
        }
    }

    public void addToDLQ(Exception ex, Map<String, Object> rawPayload, String source,
                         String sourceEventId, String alertType) {
        String payloadStr = rawPayload != null ? rawPayload.toString() : "null";
        String valueHash = extractValueHash(rawPayload);

        AlertDLQEntry entry = new AlertDLQEntry(payloadStr, ex.getMessage(),
                source, sourceEventId, alertType);

        try {
            Optional<AlertDLQEntry> existing = findExisting(valueHash, source, sourceEventId);
            if (existing.isPresent()) {
                AlertDLQEntry existingEntry = existing.get();
                existingEntry.incrementRetry();
                existingEntry.setErrorMessage(ex.getMessage());
                if (existingEntry.isMaxRetries(maxRetries)) {
                    existingEntry.setStatus(AlertDLQEntry.DLQStatus.ARCHIVED);
                    logger.info("DLQ entry archived after max retries: sourceEventId={}", sourceEventId);
                }
                dlqRepository.save(existingEntry);
            } else {
                dlqRepository.save(entry);
            }
            logger.info("Alert sent to DLQ: source={}, error={}, retryCount={}",
                    source, ex.getMessage(), entry.getRetryCount());

            try {
                Map<String, Object> dlqEventData = new java.util.HashMap<>();
                dlqEventData.put("source", source);
                dlqEventData.put("source_event_id", sourceEventId);
                dlqEventData.put("alert_type", alertType);
                dlqEventData.put("error_message", ex.getMessage());
                dlqEventData.put("retry_count", entry.getRetryCount());
                auditService.logDlqEnqueued(dlqEventData);
            } catch (Exception e) {
                logger.warn("Could not log DLQ audit event: {}", e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Failed to save DLQ entry: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredEntries() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        long deleted = dlqRepository.deleteOlderThan(cutoff);
        logger.info("DLQ cleanup completed: deleted {} entries older than {}", deleted, cutoff);
    }

    private Optional<AlertDLQEntry> findExisting(String valueHash, String source, String sourceEventId) {
        if (valueHash == null || source == null) {
            return Optional.empty();
        }
        return dlqRepository.findBySourceAndSourceEventId(source, sourceEventId);
    }

    private String extractValueHash(Map<String, Object> payload) {
        if (payload == null) return null;
        Object hash = payload.get("value_hash");
        if (hash != null) return hash.toString();
        Map<String, Object> incident = (Map<String, Object>) payload.get("incident");
        if (incident != null) {
            Object incidentHash = incident.get("value_hash");
            if (incidentHash != null) return incidentHash.toString();
        }
        return null;
    }
}
