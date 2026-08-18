package com.company.rotations.logging.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusEndpointIntegrationTest {

    @Test
    void prometheusMeterRegistry_providesPrometheusFormat() {
        PrometheusMeterRegistry prometheusRegistry = new PrometheusMeterRegistry(
            io.micrometer.prometheusmetrics.PrometheusConfig.DEFAULT);
        try {
            assertThat(prometheusRegistry).isNotNull();
            prometheusRegistry.counter("test.counter").increment();
            String output = prometheusRegistry.scrape();
            assertThat(output).contains("test_counter");
        } finally {
            prometheusRegistry.close();
        }
    }
}
