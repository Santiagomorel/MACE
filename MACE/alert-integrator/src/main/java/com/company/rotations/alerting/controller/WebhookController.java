package com.company.rotations.alerting.controller;

import com.company.rotations.alerting.AlertMetricsCollector;
import com.company.rotations.alerting.pipeline.WebhookPipeline;
import com.company.rotations.logging.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("${app.alerting.webhook.path:/api/v1/alerts}")
@Validated
public class WebhookController {

    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookPipeline webhookPipeline;
    private final AlertMetricsCollector metricsCollector;

    public WebhookController(WebhookPipeline webhookPipeline,
                             AlertMetricsCollector metricsCollector) {
        this.webhookPipeline = webhookPipeline;
        this.metricsCollector = metricsCollector;
    }

    @PostMapping
    public ResponseEntity<?> handleWebhook(
            @RequestBody Map<String, Object> rawPayload,
            HttpServletRequest request) {

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", requestId);

        String source = detectSource(rawPayload);
        long startTime = System.currentTimeMillis();
        metricsCollector.recordWebhookReceived();
        metricsCollector.recordSource(source);

        try {
            logger.info("[{}] Webhook received from source: {}", requestId, source);
            // Audit logging handled by pipeline stages
            WebhookPipeline.PipelineResult result = webhookPipeline.execute(source, rawPayload, requestId);
            long duration = System.currentTimeMillis() - startTime;
            MDC.put("durationMs", String.valueOf(duration));
            return ResponseEntity.status(result.httpStatus()).body(result.body());

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            metricsCollector.recordPipelineDuration(duration);
            metricsCollector.recordAlertFailed();

            logger.error("[{}] Pipeline error: {}", requestId, e.getMessage(), e);
            auditServiceFallback(source, rawPayload, e.getMessage(), requestId);

            MDC.remove("requestId");
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", "error",
                            "message", "Internal processing error",
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

    private void auditServiceFallback(String source, Map<String, Object> rawPayload, String errorMessage, String requestId) {
        // Fallback: minimal audit logging when AuditService is not directly available
        logger.warn("[{}] Alert processing failed for source={}: {}", requestId, source, errorMessage);
    }
}
