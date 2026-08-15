package com.company.rotations.alerting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

@Service
public class AlertMetricsCollector {

    private static final Logger logger = LoggerFactory.getLogger(AlertMetricsCollector.class);

    private final AtomicLong totalWebhooksReceived = new AtomicLong(0);
    private final AtomicLong totalWebhooksRejected = new AtomicLong(0);
    private final AtomicLong totalEventsDeduped = new AtomicLong(0);
    private final AtomicLong totalSecretsDedupedCooldown = new AtomicLong(0);
    private final AtomicLong totalSecretsDedupedInProgress = new AtomicLong(0);
    private final AtomicLong totalAlertsProcessed = new AtomicLong(0);
    private final AtomicLong totalAlertsFailed = new AtomicLong(0);
    private final AtomicLong totalAlertsSentToDlq = new AtomicLong(0);

    private final AtomicLong totalPipelineDurationMs = new AtomicLong(0);
    private final AtomicLong minPipelineDurationMs = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxPipelineDurationMs = new AtomicLong(0);

    private final ConcurrentHashMap<String, AtomicLong> adapterRouteCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> sourceCounts = new ConcurrentHashMap<>();

    public void recordWebhookReceived() {
        totalWebhooksReceived.incrementAndGet();
    }

    public void recordWebhookRejected() {
        totalWebhooksRejected.incrementAndGet();
    }

    public void recordEventDedupHit() {
        totalEventsDeduped.incrementAndGet();
    }

    public void recordSecretDedupCooldown() {
        totalSecretsDedupedCooldown.incrementAndGet();
    }

    public void recordSecretDedupInProgress() {
        totalSecretsDedupedInProgress.incrementAndGet();
    }

    public void recordAlertProcessed() {
        totalAlertsProcessed.incrementAndGet();
    }

    public void recordAlertFailed() {
        totalAlertsFailed.incrementAndGet();
    }

    public void recordAlertToDlq() {
        totalAlertsSentToDlq.incrementAndGet();
    }

    public void recordPipelineDuration(long durationMs) {
        totalPipelineDurationMs.addAndGet(durationMs);
        long currentMin = minPipelineDurationMs.get();
        while (durationMs < currentMin && !minPipelineDurationMs.compareAndSet(currentMin, durationMs)) {
            currentMin = minPipelineDurationMs.get();
        }
        long currentMax = maxPipelineDurationMs.get();
        while (durationMs > currentMax && !maxPipelineDurationMs.compareAndSet(currentMax, durationMs)) {
            currentMax = maxPipelineDurationMs.get();
        }
    }

    public void recordAdapterRoute(String adapterName) {
        adapterRouteCounts.computeIfAbsent(adapterName, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void recordSource(String source) {
        sourceCounts.computeIfAbsent(source, k -> new AtomicLong(0)).incrementAndGet();
    }

    public MetricsSnapshot getSnapshot() {
        long processed = totalAlertsProcessed.get();
        long avgDuration = processed > 0 ? totalPipelineDurationMs.get() / processed : 0;
        long actualMin = minPipelineDurationMs.get() == Long.MAX_VALUE ? 0 : minPipelineDurationMs.get();

        return new MetricsSnapshot(
                totalWebhooksReceived.get(),
                totalWebhooksRejected.get(),
                totalEventsDeduped.get(),
                totalSecretsDedupedCooldown.get(),
                totalSecretsDedupedInProgress.get(),
                processed,
                totalAlertsFailed.get(),
                totalAlertsSentToDlq.get(),
                avgDuration,
                actualMin,
                maxPipelineDurationMs.get(),
                new ConcurrentHashMap<>(adapterRouteCounts),
                new ConcurrentHashMap<>(sourceCounts)
        );
    }

    public void reset() {
        totalWebhooksReceived.set(0);
        totalWebhooksRejected.set(0);
        totalEventsDeduped.set(0);
        totalSecretsDedupedCooldown.set(0);
        totalSecretsDedupedInProgress.set(0);
        totalAlertsProcessed.set(0);
        totalAlertsFailed.set(0);
        totalAlertsSentToDlq.set(0);
        totalPipelineDurationMs.set(0);
        minPipelineDurationMs.set(Long.MAX_VALUE);
        maxPipelineDurationMs.set(0);
        adapterRouteCounts.clear();
        sourceCounts.clear();
    }

    @Scheduled(cron = "0 */5 * * * ?")
    public void logMetrics() {
        MetricsSnapshot snapshot = getSnapshot();
        logger.info("AlertMetrics: webhooks={}, processed={}, eventDedup={}, secretDedupCooldown={}, "
                        + "secretDedupInProgress={}, failed={}, dlq={}, avgDurationMs={}, minDurationMs={}, maxDurationMs={}",
                snapshot.totalWebhooksReceived(),
                snapshot.totalAlertsProcessed(),
                snapshot.totalEventsDeduped(),
                snapshot.totalSecretsDedupedCooldown(),
                snapshot.totalSecretsDedupedInProgress(),
                snapshot.totalAlertsFailed(),
                snapshot.totalAlertsSentToDlq(),
                snapshot.avgPipelineDurationMs(),
                snapshot.minPipelineDurationMs(),
                snapshot.maxPipelineDurationMs());

        if (!snapshot.adapterRouteCounts().isEmpty()) {
            logger.info("Adapter routing stats: {}", snapshot.adapterRouteCounts());
        }
        if (!snapshot.sourceCounts().isEmpty()) {
            logger.info("Source counts: {}", snapshot.sourceCounts());
        }
    }

    public record MetricsSnapshot(
            long totalWebhooksReceived,
            long totalWebhooksRejected,
            long totalEventsDeduped,
            long totalSecretsDedupedCooldown,
            long totalSecretsDedupedInProgress,
            long totalAlertsProcessed,
            long totalAlertsFailed,
            long totalAlertsSentToDlq,
            long avgPipelineDurationMs,
            long minPipelineDurationMs,
            long maxPipelineDurationMs,
            ConcurrentHashMap<String, AtomicLong> adapterRouteCounts,
            ConcurrentHashMap<String, AtomicLong> sourceCounts
    ) {
    }
}
