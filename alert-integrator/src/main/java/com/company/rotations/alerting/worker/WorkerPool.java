package com.company.rotations.alerting.worker;

import com.company.rotations.alerting.dedup.SecretDedupService;
import com.company.rotations.alerting.dlq.AlertDLQEntry;
import com.company.rotations.alerting.dlq.DeadLetterQueueService;
import com.company.rotations.alerting.model.WebhookPayload;
import com.company.rotations.logging.service.AuditService;
import com.company.rotations.models.GenericAlertModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class WorkerPool {

    private static final Logger logger = LoggerFactory.getLogger(WorkerPool.class);

    private final ExecutorService executorService;
    private final BlockingQueue<WebhookPayload> alertQueue;
    private final DeadLetterQueueService dlqService;
    private final SecretDedupService secretDedupService;
    private final AuditService auditService;
    private final int poolSize;
    private final int queueMaxSize;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);

    public WorkerPool(
            @Value("${app.alerting.worker-pool-size:5}") int poolSize,
            @Value("${app.alerting.queue-max-size:1000}") int queueMaxSize,
            DeadLetterQueueService dlqService,
            SecretDedupService secretDedupService,
            AuditService auditService) {
        this.poolSize = poolSize;
        this.queueMaxSize = queueMaxSize;
        this.alertQueue = new LinkedBlockingQueue<>(queueMaxSize);
        this.dlqService = dlqService;
        this.secretDedupService = secretDedupService;
        this.auditService = auditService;
        this.executorService = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r);
            t.setName("alert-worker-" + t.getId());
            t.setDaemon(true);
            return t;
        });
    }

    public boolean start() {
        if (running.compareAndSet(false, true)) {
            for (int i = 0; i < poolSize; i++) {
                executorService.submit(this::workerLoop);
            }
            logger.info("WorkerPool started with {} workers, max queue size={}", poolSize, queueMaxSize);
            return true;
        }
        return false;
    }

    public void stop() {
        running.set(false);
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("WorkerPool stopped. Processed={}, Failed={}",
                processedCount.get(), failedCount.get());
    }

    public boolean submit(WebhookPayload payload) throws RejectedExecutionException, InterruptedException {
        if (!running.get()) {
            throw new RejectedExecutionException("WorkerPool is not running");
        }
        boolean offered = alertQueue.offer(payload);
        if (!offered) {
            logger.warn("Queue full (size={}, max={}), DLQing alert from source={}",
                    alertQueue.size(), queueMaxSize, payload.source());
            dlqService.addToDLQ(
                    new RuntimeException("Queue full, backpressure applied"),
                    parseRawPayload(payload), payload.source(),
                    payload.alert().getSourceEventId(), "backpressure");
            return false;
        }
        return true;
    }

    private void workerLoop() {
        Thread.currentThread().setName("alert-worker-" + Thread.currentThread().getId());
        logger.debug("Worker started: {}", Thread.currentThread().getName());

        while (running.get()) {
            WebhookPayload payload;
            try {
                payload = alertQueue.poll(5, TimeUnit.SECONDS);
                if (payload == null) continue;

                processAlert(payload);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Worker loop error: {}", e.getMessage(), e);
            }
        }
        logger.debug("Worker stopped: {}", Thread.currentThread().getName());
    }

    private void processAlert(WebhookPayload payload) {
        GenericAlertModel alert = payload.alert();
        String eventId = alert.getEventId();
        String source = payload.source();

        try {
            auditService.logProcessingStarted(source, eventId);
            logger.info("Processing alert: eventId={}, source={}, secretType={}",
                    eventId, source, alert.getDetectedSecret() != null ? alert.getDetectedSecret().getType() : "unknown");

            String valueHash = alert.getDetectedSecret() != null ? alert.getDetectedSecret().getValueHash() : null;
            String repository = alert.getContext() != null ? alert.getContext().getRepository() : null;

            if (valueHash != null && secretDedupService.getStatus(valueHash, repository) != null) {
                logger.info("Alert skipped: secret still in dedup for eventId={}", eventId);
            }

            processedCount.incrementAndGet();
            auditService.logProcessingCompleted(source, eventId);
            logger.info("Alert processing completed: eventId={}", eventId);

        } catch (Exception e) {
            failedCount.incrementAndGet();
            logger.error("Alert processing failed: eventId={}, error={}", eventId, e.getMessage());
            auditService.logProcessingFailed(source, parseRawPayload(payload), e.getMessage());

            try {
                dlqService.addToDLQ(e, parseRawPayload(payload), source,
                        alert.getSourceEventId(), "worker_processing");
            } catch (Exception dlqEx) {
                logger.error("Failed to send to DLQ: {}", dlqEx.getMessage());
            }
        }
    }

    private Map<String, Object> parseRawPayload(WebhookPayload payload) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(payload.rawBody(), Map.class);
        } catch (Exception e) {
            return Map.of("raw", payload.rawBody());
        }
    }

    @Scheduled(cron = "0 */5 * * * ?")
    public void reportMetrics() {
        logger.info("WorkerPool metrics: queueSize={}, processed={}, failed={}, active={}",
                alertQueue.size(), processedCount.get(), failedCount.get(),
                ((ThreadPoolExecutor) executorService).getActiveCount());
    }

    public int getQueueSize() { return alertQueue.size(); }
    public int getProcessedCount() { return processedCount.get(); }
    public int getFailedCount() { return failedCount.get(); }
    public int getPoolSize() { return poolSize; }
    public boolean isRunning() { return running.get(); }
}
