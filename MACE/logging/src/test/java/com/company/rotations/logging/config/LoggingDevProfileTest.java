package com.company.rotations.logging.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import com.company.rotations.logging.service.AuditService;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = { LoggingAutoConfiguration.class })
@ActiveProfiles("dev")
@TestPropertySource(properties = {
    "spring.main.web-application-type=none",
    "spring.jpa.hibernate.ddl-auto=none"
})
class LoggingDevProfileTest {

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("Dev profile should be active")
    void devProfileShouldBeActive() {
        String[] activeProfiles = environment.getActiveProfiles();
        assertTrue(java.util.Arrays.asList(activeProfiles).contains("dev"),
                "Dev profile should be active: " + java.util.Arrays.toString(activeProfiles));
    }

    @Test
    @DisplayName("Dev profile should have DEBUG logging for logging module")
    void devProfileShouldHaveDebugLogging() {
        String loggingLogLevel = environment.getProperty("logging.level.com.company.rotations.logging");
        assertNotNull(loggingLogLevel, "Logging log level should be configured");
        assertTrue(loggingLogLevel.equals("DEBUG") || loggingLogLevel.equals("debug"),
                "Dev profile should have DEBUG logging level: " + loggingLogLevel);
    }

    @Test
    @DisplayName("Dev profile should have DEBUG logging for alerting")
    void devProfileShouldHaveAlertingDebugLogging() {
        String alertingLogLevel = environment.getProperty("logging.level.com.company.rotations.alerting");
        assertNotNull(alertingLogLevel, "Alerting log level should be configured");
        assertTrue(alertingLogLevel.equals("DEBUG") || alertingLogLevel.equals("debug"),
                "Dev profile should have DEBUG alerting log level: " + alertingLogLevel);
    }

    @Test
    @DisplayName("Dev profile should have DEBUG logging for verification")
    void devProfileShouldHaveVerificationDebugLogging() {
        String verificationLogLevel = environment.getProperty("logging.level.com.company.rotations.verification");
        assertNotNull(verificationLogLevel, "Verification log level should be configured");
        assertTrue(verificationLogLevel.equals("DEBUG") || verificationLogLevel.equals("debug"),
                "Dev profile should have DEBUG verification log level: " + verificationLogLevel);
    }

    @Test
    @DisplayName("Dev profile should have DEBUG logging for decision")
    void devProfileShouldHaveDecisionDebugLogging() {
        String decisionLogLevel = environment.getProperty("logging.level.com.company.rotations.decision");
        assertNotNull(decisionLogLevel, "Decision log level should be configured");
        assertTrue(decisionLogLevel.equals("DEBUG") || decisionLogLevel.equals("debug"),
                "Dev profile should have DEBUG decision log level: " + decisionLogLevel);
    }

    @Test
    @DisplayName("Dev profile should have DEBUG logging for action executor")
    void devProfileShouldHaveActionExecutorDebugLogging() {
        String actionExecutorLogLevel = environment.getProperty("logging.level.com.company.rotations.actionexecutor");
        assertNotNull(actionExecutorLogLevel, "Action executor log level should be configured");
        assertTrue(actionExecutorLogLevel.equals("DEBUG") || actionExecutorLogLevel.equals("debug"),
                "Dev profile should have DEBUG action executor log level: " + actionExecutorLogLevel);
    }

    @Test
    @DisplayName("Logging auto configuration should load beans")
    void loggingAutoConfigurationShouldLoadBeans() {
        assertNotNull(context, "ApplicationContext should be loaded");
        assertTrue(context.containsBean("mdcLoggingFilter"),
                "MDC logging filter bean should be present");
    }
}
