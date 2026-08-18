package com.company.rotations.alerting.config;

import com.company.rotations.alerting.interceptor.ApiKeyInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebConfigTest {

    private ApiKeyInterceptor apiKeyInterceptor;
    private WebConfig webConfig;

    @BeforeEach
    void setUp() {
        apiKeyInterceptor = mock(ApiKeyInterceptor.class);
        webConfig = new WebConfig(apiKeyInterceptor);
    }

    @Test
    @DisplayName("Should register interceptor without throwing")
    void shouldRegisterInterceptorWithoutThrowing() {
        InterceptorRegistry registry = new InterceptorRegistry();
        assertDoesNotThrow(() -> webConfig.addInterceptors(registry));
    }

    @Test
    @DisplayName("Should create with mock interceptor")
    void shouldCreateWithMockInterceptor() {
        assertNotNull(webConfig);
        assertNotNull(apiKeyInterceptor);
    }
}
