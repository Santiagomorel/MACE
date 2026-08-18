package com.company.rotations.alerting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

import static org.junit.jupiter.api.Assertions.*;

class AlertIntegratorApplicationTest {

    @Test
    @DisplayName("Should have main method")
    void shouldHaveMainMethod() {
        // Just verify the class exists and can be loaded
        assertNotNull(AlertIntegratorApplication.class);
        assertDoesNotThrow(() -> {
            AlertIntegratorApplication.class.getMethod("main", String[].class);
        });
    }

    @Test
    @DisplayName("Application class should be in correct package")
    void shouldBeInCorrectPackage() {
        assertEquals("com.company.rotations.alerting",
                AlertIntegratorApplication.class.getPackage().getName());
    }
}
