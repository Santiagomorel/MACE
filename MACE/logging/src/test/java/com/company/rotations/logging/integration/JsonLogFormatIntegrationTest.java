package com.company.rotations.logging.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Integration Tests - JSON Log Format with MDC Fields")
class JsonLogFormatIntegrationTest {

    private ListAppender<ILoggingEvent> listAppender;
    private ch.qos.logback.classic.Logger logger;

    @BeforeEach
    void setUp() {
        listAppender = new ListAppender<>();
        listAppender.start();

        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(JsonLogFormatIntegrationTest.class);
        logger.setLevel(Level.DEBUG);
        logger.addAppender(listAppender);
    }

    @Test
    @DisplayName("MDC trace_id is captured in logging event")
    void testMdcTraceIdCaptured() {
        org.slf4j.MDC.put("trace_id", "trace-test-abc-123");
        logger.info("Test log message with trace_id");

        List<ILoggingEvent> events = listAppender.list;
        assertFalse(events.isEmpty());

        Map<String, String> mdc = events.get(0).getMDCPropertyMap();
        assertEquals("trace-test-abc-123", mdc.get("trace_id"));
    }

    @Test
    @DisplayName("MDC alert_id is captured in logging event")
    void testMdcAlertIdCaptured() {
        org.slf4j.MDC.put("alert_id", "alert-integration-test-001");
        logger.info("Test log message with alert_id");

        List<ILoggingEvent> events = listAppender.list;
        assertFalse(events.isEmpty());

        Map<String, String> mdc = events.get(0).getMDCPropertyMap();
        assertEquals("alert-integration-test-001", mdc.get("alert_id"));
    }

    @Test
    @DisplayName("MDC client_id is captured in logging event")
    void testMdcClientIdCaptured() {
        org.slf4j.MDC.put("client_id", "tenant-acme-corp");
        logger.info("Test log message with client_id");

        List<ILoggingEvent> events = listAppender.list;
        assertFalse(events.isEmpty());

        Map<String, String> mdc = events.get(0).getMDCPropertyMap();
        assertEquals("tenant-acme-corp", mdc.get("client_id"));
    }

    @Test
    @DisplayName("MDC phase is captured in logging event")
    void testMdcPhaseCaptured() {
        org.slf4j.MDC.put("phase", "verification");
        logger.info("Test log message with phase");

        List<ILoggingEvent> events = listAppender.list;
        assertFalse(events.isEmpty());

        Map<String, String> mdc = events.get(0).getMDCPropertyMap();
        assertEquals("verification", mdc.get("phase"));
    }

    @Test
    @DisplayName("MDC step is captured in logging event")
    void testMdcStepCaptured() {
        org.slf4j.MDC.put("step", "credential-validation");
        logger.info("Test log message with step");

        List<ILoggingEvent> events = listAppender.list;
        assertFalse(events.isEmpty());

        Map<String, String> mdc = events.get(0).getMDCPropertyMap();
        assertEquals("credential-validation", mdc.get("step"));
    }

    @Test
    @DisplayName("All MDC fields captured together in single logging event")
    void testAllMdcFieldsCapturedTogether() {
        org.slf4j.MDC.put("trace_id", "trace-all-fields-001");
        org.slf4j.MDC.put("alert_id", "alert-all-fields-001");
        org.slf4j.MDC.put("client_id", "client-all-001");
        org.slf4j.MDC.put("phase", "action");
        org.slf4j.MDC.put("step", "rotation");
        org.slf4j.MDC.put("duration_ms", "1234");
        org.slf4j.MDC.put("status", "SUCCESS");

        logger.info("Test log message with all MDC fields");

        List<ILoggingEvent> events = listAppender.list;
        assertFalse(events.isEmpty());

        Map<String, String> mdc = events.get(0).getMDCPropertyMap();

        assertEquals("trace-all-fields-001", mdc.get("trace_id"));
        assertEquals("alert-all-fields-001", mdc.get("alert_id"));
        assertEquals("client-all-001", mdc.get("client_id"));
        assertEquals("action", mdc.get("phase"));
        assertEquals("rotation", mdc.get("step"));
        assertEquals("1234", mdc.get("duration_ms"));
        assertEquals("SUCCESS", mdc.get("status"));
    }

    @Test
    @DisplayName("Log message is captured in event")
    void testMessageCapturedInEvent() {
        logger.info("Integration test message content");

        List<ILoggingEvent> events = listAppender.list;
        assertFalse(events.isEmpty());

        ILoggingEvent event = events.get(0);
        assertEquals("Integration test message content", event.getMessage());
    }

    @Test
    @DisplayName("Log level is captured in event")
    void testLevelCapturedInEvent() {
        logger.info("Test log message with level");

        List<ILoggingEvent> events = listAppender.list;
        assertFalse(events.isEmpty());

        ILoggingEvent event = events.get(0);
        assertEquals(Level.INFO, event.getLevel());
    }

    @Test
    @DisplayName("MDC fields are cleared after logging")
    void testMdcClearedAfterLogging() {
        org.slf4j.MDC.put("trace_id", "trace-before-clear");
        org.slf4j.MDC.put("alert_id", "alert-before-clear");

        logger.info("Before MDC clear");
        listAppender.stop();

        assertEquals("trace-before-clear", org.slf4j.MDC.get("trace_id"));
        assertEquals("alert-before-clear", org.slf4j.MDC.get("alert_id"));

        org.slf4j.MDC.clear();
        assertNull(org.slf4j.MDC.get("trace_id"));
        assertNull(org.slf4j.MDC.get("alert_id"));
    }

    @Test
    @DisplayName("Missing MDC fields do not cause errors")
    void testMissingMdcFieldsDoNotCauseError() {
        org.slf4j.MDC.clear();

        assertDoesNotThrow(() -> logger.info("Test with no MDC fields"));

        List<ILoggingEvent> events = listAppender.list;
        assertFalse(events.isEmpty());
    }

    @Test
    @DisplayName("Multiple log lines in same context share MDC fields")
    void testMultipleLogLinesShareMdcFields() {
        org.slf4j.MDC.put("trace_id", "trace-multi-line-001");
        org.slf4j.MDC.put("phase", "webhook");

        logger.info("First log line");
        logger.info("Second log line");
        logger.info("Third log line");

        List<ILoggingEvent> events = listAppender.list;
        assertEquals(3, events.size());

        for (ILoggingEvent event : events) {
            Map<String, String> mdc = event.getMDCPropertyMap();
            assertEquals("trace-multi-line-001", mdc.get("trace_id"));
            assertEquals("webhook", mdc.get("phase"));
        }
    }

    @Test
    @DisplayName("MDC fields differ across separate contexts")
    void testMdcFieldsDifferAcrossContexts() {
        org.slf4j.MDC.put("trace_id", "trace-context-1");
        org.slf4j.MDC.put("phase", "webhook");
        logger.info("Context 1");

        org.slf4j.MDC.put("trace_id", "trace-context-2");
        org.slf4j.MDC.put("phase", "verification");
        logger.info("Context 2");

        List<ILoggingEvent> events = listAppender.list;
        assertEquals(2, events.size());

        Map<String, String> mdc1 = events.get(0).getMDCPropertyMap();
        Map<String, String> mdc2 = events.get(1).getMDCPropertyMap();

        assertEquals("trace-context-1", mdc1.get("trace_id"));
        assertEquals("webhook", mdc1.get("phase"));

        assertEquals("trace-context-2", mdc2.get("trace_id"));
        assertEquals("verification", mdc2.get("phase"));
    }

    @Test
    @DisplayName("Logger name is captured in event")
    void testLoggerNameCaptured() {
        logger.info("Test message for logger name");

        List<ILoggingEvent> events = listAppender.list;
        assertFalse(events.isEmpty());

        ILoggingEvent event = events.get(0);
        assertEquals("com.company.rotations.logging.integration.JsonLogFormatIntegrationTest",
                event.getLoggerName());
    }

    @Test
    @DisplayName("MDC fields persist across INFO and WARN level logs")
    void testMdcFieldsAcrossLogLevels() {
        org.slf4j.MDC.put("trace_id", "trace-levels-001");
        org.slf4j.MDC.put("alert_id", "alert-levels-001");

        logger.info("Info level message");
        logger.warn("Warn level message");
        logger.error("Error level message");

        List<ILoggingEvent> events = listAppender.list;
        assertEquals(3, events.size());

        for (ILoggingEvent event : events) {
            Map<String, String> mdc = event.getMDCPropertyMap();
            assertEquals("trace-levels-001", mdc.get("trace_id"));
            assertEquals("alert-levels-001", mdc.get("alert_id"));
        }
    }
}
