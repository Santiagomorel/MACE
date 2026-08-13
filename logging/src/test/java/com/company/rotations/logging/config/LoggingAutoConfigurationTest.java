package com.company.rotations.logging.config;

import com.company.rotations.logging.filter.MdcLoggingFilter;
import com.company.rotations.logging.service.AuditPurgeService;
import com.company.rotations.logging.service.AuditService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.slf4j.MDC;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoggingAutoConfigurationTest {

    @Test
    void mdcLoggingFilterBean_createsFilter() {
        LoggingAutoConfiguration config = new LoggingAutoConfiguration();
        MdcLoggingFilter filter = config.mdcLoggingFilter();
        assertNotNull(filter);
        assertTrue(filter.getOrder() < 0);
    }

    @Test
    void auditServiceBean_createsService() {
        var repo = mock(com.company.rotations.logging.repository.AuditEventRepository.class);
        LoggingAutoConfiguration config = new LoggingAutoConfiguration();
        AuditService service = config.auditService(repo);
        assertNotNull(service);
    }

    @Test
    void auditPurgeServiceBean_createsService() {
        var repo = mock(com.company.rotations.logging.repository.AuditEventRepository.class);
        LoggingAutoConfiguration config = new LoggingAutoConfiguration();
        AuditPurgeService service = config.auditPurgeService(repo);
        assertNotNull(service);
    }

    @Test
    void pipelineDurationTrackerBean_createsTracker() {
        var registry = mock(MeterRegistry.class);
        LoggingAutoConfiguration config = new LoggingAutoConfiguration();
        var tracker = config.pipelineDurationTracker(registry);
        assertNotNull(tracker);
    }

    @Test
    void metricsConfiguration_createsDedupMetrics() {
        var registry = mock(MeterRegistry.class);
        var metricsConfig = new MetricsConfiguration();
        var dedupMetrics = metricsConfig.dedupMetrics(registry);
        assertNotNull(dedupMetrics);
    }

    @Test
    void metricsConfiguration_createsWebhookMetrics() {
        var registry = mock(MeterRegistry.class);
        var metricsConfig = new MetricsConfiguration();
        var webhookMetrics = metricsConfig.webhookMetrics(registry);
        assertNotNull(webhookMetrics);
    }
}
