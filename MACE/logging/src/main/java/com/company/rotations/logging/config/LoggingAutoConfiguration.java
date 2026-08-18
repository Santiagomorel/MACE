package com.company.rotations.logging.config;

import com.company.rotations.logging.filter.MdcLoggingFilter;
import com.company.rotations.logging.service.AuditPurgeService;
import com.company.rotations.logging.service.AuditService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Configuration
@ConditionalOnClass({AuditService.class, MdcLoggingFilter.class})
@EnableScheduling
public class LoggingAutoConfiguration {

    @Bean
    public MdcLoggingFilter mdcLoggingFilter() {
        return new MdcLoggingFilter();
    }

    @Bean
    public AuditService auditService(
            org.springframework.context.ApplicationContext context) {
        if (context.containsBean("auditEventRepository")) {
            return new AuditService(context.getBean(com.company.rotations.logging.repository.AuditEventRepository.class));
        }
        return new AuditService();
    }

    @Bean
    @ConditionalOnBean(com.company.rotations.logging.repository.AuditEventRepository.class)
    public AuditPurgeService auditPurgeService(com.company.rotations.logging.repository.AuditEventRepository repository) {
        return new AuditPurgeService(repository);
    }

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    public PipelineDurationTracker pipelineDurationTracker(MeterRegistry meterRegistry) {
        return new PipelineDurationTracker(meterRegistry);
    }
}
