package com.company.rotations.alerting.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.PrintWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiKeyInterceptorTest {

    private ApiKeyInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @Nested
    @DisplayName("With API Key Enabled")
    class ApiKeyEnabledTests {

        @BeforeEach
        void setUp() throws Exception {
            interceptor = new ApiKeyInterceptor("secret-key-123", true);
            request = mock(HttpServletRequest.class);
            response = mock(HttpServletResponse.class);
            PrintWriter writer = mock(PrintWriter.class);
            when(response.getWriter()).thenReturn(writer);
        }

        @Test
        @DisplayName("Should return true for valid API key on admin endpoint")
        void shouldReturnTrueForValidApiKeyOnAdminEndpoint() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/v1/admin/rules");
            when(request.getHeader("X-API-Key")).thenReturn("secret-key-123");
            when(request.getMethod()).thenReturn("GET");

            boolean result = interceptor.preHandle(request, response, null);

            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false for missing API key on admin endpoint")
        void shouldReturnFalseForMissingApiKeyOnAdminEndpoint() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/v1/admin/rules");
            when(request.getHeader("X-API-Key")).thenReturn(null);
            when(request.getMethod()).thenReturn("GET");

            boolean result = interceptor.preHandle(request, response, null);

            assertFalse(result);
            verify(response).setStatus(401);
        }

        @Test
        @DisplayName("Should return false for blank API key on admin endpoint")
        void shouldReturnFalseForBlankApiKeyOnAdminEndpoint() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/v1/admin/rules");
            when(request.getHeader("X-API-Key")).thenReturn("   ");
            when(request.getMethod()).thenReturn("GET");

            boolean result = interceptor.preHandle(request, response, null);

            assertFalse(result);
            verify(response).setStatus(401);
        }

        @Test
        @DisplayName("Should return false for invalid API key on admin endpoint")
        void shouldReturnFalseForInvalidApiKeyOnAdminEndpoint() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/v1/admin/rules");
            when(request.getHeader("X-API-Key")).thenReturn("wrong-key");
            when(request.getMethod()).thenReturn("GET");

            boolean result = interceptor.preHandle(request, response, null);

            assertFalse(result);
            verify(response).setStatus(403);
        }

        @Test
        @DisplayName("Should return true for non-admin endpoints without API key")
        void shouldReturnTrueForNonAdminEndpointsWithoutApiKey() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/v1/alerts");
            when(request.getMethod()).thenReturn("POST");

            boolean result = interceptor.preHandle(request, response, null);

            assertTrue(result);
            verify(response, never()).setStatus(anyInt());
        }

        @Test
        @DisplayName("Should allow OPTIONS requests on admin endpoints")
        void shouldAllowOptionsOnAdminEndpoints() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/v1/admin/rules");
            when(request.getMethod()).thenReturn("OPTIONS");

            boolean result = interceptor.preHandle(request, response, null);

            assertTrue(result);
            verify(response, never()).setStatus(anyInt());
        }

        @Test
        @DisplayName("Should return true when admin feature is disabled")
        void shouldReturnTrueWhenAdminFeatureDisabled() throws Exception {
            ApiKeyInterceptor disabledInterceptor = new ApiKeyInterceptor("secret-key-123", false);
            when(request.getRequestURI()).thenReturn("/api/v1/admin/rules");
            when(request.getHeader("X-API-Key")).thenReturn(null);
            when(request.getMethod()).thenReturn("GET");

            boolean result = disabledInterceptor.preHandle(request, response, null);

            assertTrue(result);
            verify(response, never()).setStatus(anyInt());
        }

        @Test
        @DisplayName("Should return false when API key is not configured but enforcement is on")
        void shouldReturnFalseWhenApiKeyNotConfigured() throws Exception {
            ApiKeyInterceptor noKeyInterceptor = new ApiKeyInterceptor("", true);
            when(request.getRequestURI()).thenReturn("/api/v1/admin/rules");
            when(request.getHeader("X-API-Key")).thenReturn("any-key");
            when(request.getMethod()).thenReturn("GET");

            boolean result = noKeyInterceptor.preHandle(request, response, null);

            assertFalse(result);
            verify(response).setStatus(403);
        }

        @Test
        @DisplayName("Should accept valid key for different admin endpoints")
        void shouldAcceptValidKeyForDifferentAdminEndpoints() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/v1/admin/rules/upload");
            when(request.getHeader("X-API-Key")).thenReturn("secret-key-123");
            when(request.getMethod()).thenReturn("POST");

            boolean result = interceptor.preHandle(request, response, null);

            assertTrue(result);
        }

        @Test
        @DisplayName("Should reject valid key on non-admin endpoints (no key required)")
        void shouldNotRequireKeyOnNonAdminEndpoints() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/v1/verification/123");
            when(request.getMethod()).thenReturn("GET");

            boolean result = interceptor.preHandle(request, response, null);

            assertTrue(result);
            verify(response, never()).setStatus(anyInt());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty URI")
        void shouldHandleEmptyUri() throws Exception {
            interceptor = new ApiKeyInterceptor("secret-key-123", true);
            request = mock(HttpServletRequest.class);
            response = mock(HttpServletResponse.class);
            PrintWriter writer = mock(PrintWriter.class);
            when(response.getWriter()).thenReturn(writer);
            when(request.getRequestURI()).thenReturn("");
            when(request.getMethod()).thenReturn("GET");

            boolean result = interceptor.preHandle(request, response, null);

            assertTrue(result);
        }

        @Test
        @DisplayName("Should handle admin path with trailing slashes")
        void shouldHandleAdminPathWithTrailingSlashes() throws Exception {
            interceptor = new ApiKeyInterceptor("secret-key-123", true);
            request = mock(HttpServletRequest.class);
            response = mock(HttpServletResponse.class);
            PrintWriter writer = mock(PrintWriter.class);
            when(response.getWriter()).thenReturn(writer);
            when(request.getRequestURI()).thenReturn("/api/v1/admin/");
            when(request.getHeader("X-API-Key")).thenReturn("secret-key-123");
            when(request.getMethod()).thenReturn("GET");

            boolean result = interceptor.preHandle(request, response, null);

            assertTrue(result);
        }
    }
}
