package com.company.rotations.alerting.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.lang.annotation.Annotation;

import static org.junit.jupiter.api.Assertions.*;

class CorsConfigTest {

    @Test
    @DisplayName("Should be a Spring Configuration")
    void shouldBeConfiguration() {
        Annotation[] annotations = CorsConfig.class.getAnnotations();
        boolean hasConfig = false;
        for (Annotation a : annotations) {
            if (a.annotationType().getName().contains("Configuration")) {
                hasConfig = true;
                break;
            }
        }
        assertTrue(hasConfig);
    }

    @Test
    @DisplayName("Should implement WebMvcConfigurer")
    void shouldImplementWebMvcConfigurer() {
        assertTrue(WebMvcConfigurer.class.isAssignableFrom(CorsConfig.class));
    }

    @Test
    @DisplayName("CorsConfig class should exist and be loadable")
    void shouldBeLoadable() {
        assertNotNull(CorsConfig.class);
    }

    @Test
    @DisplayName("Should have addCorsMappings method")
    void shouldHaveAddCorsMappings() {
        boolean hasMethod = false;
        for (var method : CorsConfig.class.getMethods()) {
            if (method.getName().equals("addCorsMappings")) {
                hasMethod = true;
                break;
            }
        }
        assertTrue(hasMethod);
    }
}
