package com.company.rotations.logging.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import java.util.concurrent.atomic.AtomicInteger;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfiguration {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags("application", "rotation-system");
    }

    @Bean
    public DedupMetrics dedupMetrics(MeterRegistry meterRegistry) {
        return new DedupMetrics(meterRegistry);
    }

    @Bean
    public WebhookMetrics webhookMetrics(MeterRegistry meterRegistry) {
        return new WebhookMetrics(meterRegistry);
    }

    @Bean
    public CircuitBreakerMetrics circuitBreakerMetrics(MeterRegistry meterRegistry) {
        return new CircuitBreakerMetrics(meterRegistry);
    }

    @Bean
    public AuditMetrics auditMetrics(MeterRegistry meterRegistry) {
        return new AuditMetrics(meterRegistry);
    }

    public static class DedupMetrics {
        private final Counter dedupHits;
        private final Counter dedupMisses;

        public DedupMetrics(MeterRegistry meterRegistry) {
            this.dedupHits = Counter.builder("app.dedup.hits")
                .description("Number of duplicate alerts detected")
                .register(meterRegistry);
            this.dedupMisses = Counter.builder("app.dedup.misses")
                .description("Number of unique alerts that passed dedup")
                .register(meterRegistry);
        }

        public void incrementHit() { dedupHits.increment(); }
        public void incrementMiss() { dedupMisses.increment(); }
    }

    public static class WebhookMetrics {
        private final Counter webhookReceived;
        private final Counter webhookFailed;

        public WebhookMetrics(MeterRegistry meterRegistry) {
            this.webhookReceived = Counter.builder("app.webhook.received")
                .description("Number of webhooks received")
                .register(meterRegistry);
            this.webhookFailed = Counter.builder("app.webhook.failed")
                .description("Number of webhooks that failed validation")
                .register(meterRegistry);
        }

        public void incrementReceived() { webhookReceived.increment(); }
        public void incrementFailed() { webhookFailed.increment(); }
    }

    public static class CircuitBreakerMetrics {
        private final AtomicInteger state = new AtomicInteger(0);

        public CircuitBreakerMetrics(MeterRegistry meterRegistry) {
            Gauge.builder("app.circuit.breaker.state", state, AtomicInteger::get)
                .description("Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)")
                .register(meterRegistry);
        }

        public void setState(int newState) { state.set(newState); }
        public int getState() { return state.get(); }
    }

    public static class AuditMetrics {
        public AuditMetrics(MeterRegistry meterRegistry) {
            Gauge.builder("app.audit.events.count",
                new AtomicInteger(0),
                AtomicInteger::doubleValue)
                .tag("type", "total")
                .description("Total number of audit events")
                .register(meterRegistry);
        }
    }
}
