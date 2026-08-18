package com.company.rotations.logging.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
class LoggingDevProfileTest {

    @Autowired
    private Environment environment;

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
    @DisplayName("Dev profile should expose additional actuator endpoints")
    void devProfileShouldExposeAdditionalActuatorEndpoints() {
        String includedEndpoints = environment.getProperty("management.endpoints.web.exposure.include");
        assertNotNull(includedEndpoints, "Actuator endpoints should be configured");
        assertTrue(includedEndpoints.contains("env"),
                "Dev profile should expose 'env' endpoint: " + includedEndpoints);
        assertTrue(includedEndpoints.contains("conditions"),
                "Dev profile should expose 'conditions' endpoint: " + includedEndpoints);
    }

    @Test
    @DisplayName("Dev profile should show health details always")
    void devProfileShouldShowHealthDetailsAlways() {
        String healthDetails = environment.getProperty("management.endpoint.health.show-details");
        assertNotNull(healthDetails, "Health show-details should be configured");
        assertEquals("always", healthDetails,
                "Dev profile should show health details always");
    }
}
