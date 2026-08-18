package com.company.rotations.alerting.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
class AlertIntegratorDevProfileTest {

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
    @DisplayName("Dev profile should use H2 database")
    void devProfileShouldUseH2() {
        String datasourceUrl = environment.getProperty("spring.datasource.url");
        assertNotNull(datasourceUrl, "Datasource URL should be configured");
        assertTrue(datasourceUrl.contains("h2"),
                "Dev profile should use H2 database: " + datasourceUrl);
    }

    @Test
    @DisplayName("Dev profile should use create-drop DDL auto")
    void devProfileShouldUseCreateDrop() {
        String ddlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto");
        assertNotNull(ddlAuto, "DDL auto should be configured");
        assertEquals("create-drop", ddlAuto,
                "Dev profile should use create-drop DDL auto");
    }

    @Test
    @DisplayName("Dev profile should have Flyway disabled")
    void devProfileShouldDisableFlyway() {
        String flywayEnabled = environment.getProperty("spring.flyway.enabled");
        assertNotNull(flywayEnabled, "Flyway enabled should be configured");
        assertEquals("false", flywayEnabled,
                "Dev profile should have Flyway disabled");
    }

    @Test
    @DisplayName("Dev profile should have DEBUG logging")
    void devProfileShouldHaveDebugLogging() {
        String alertingLogLevel = environment.getProperty("logging.level.com.company.rotations.alerting");
        assertNotNull(alertingLogLevel, "Alerting log level should be configured");
        assertTrue(alertingLogLevel.equals("DEBUG") || alertingLogLevel.equals("debug"),
                "Dev profile should have DEBUG logging level: " + alertingLogLevel);
    }

    @Test
    @DisplayName("Dev profile should allow bean definition overriding")
    void devProfileShouldAllowBeanOverride() {
        String allowOverride = environment.getProperty("spring.main.allow-bean-definition-overriding");
        assertNotNull(allowOverride, "Bean overriding should be configured");
        assertEquals("true", allowOverride,
                "Dev profile should allow bean definition overriding");
    }
}
