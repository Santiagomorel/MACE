package com.company.rotations.logging.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@ConditionalOnBean(MeterRegistry.class)
@Component
public class PipelineDurationTracker {

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, Timer.Sample> activeTimers = new ConcurrentHashMap<>();

    public PipelineDurationTracker(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void startTimer(String traceId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        activeTimers.put(traceId, sample);
    }

    public void stopTimer(String traceId) {
        Timer.Sample sample = activeTimers.remove(traceId);
        if (sample != null) {
            sample.stop(Timer.builder("app.pipeline.duration")
                .description("Pipeline processing duration")
                .register(meterRegistry));
        }
    }

    public void stopTimer(String traceId, long durationMs) {
        activeTimers.remove(traceId);
        Timer timer = Timer.builder("app.pipeline.duration")
            .description("Pipeline processing duration")
            .register(meterRegistry);
        timer.record(durationMs, TimeUnit.MILLISECONDS);
    }
}
