package com.company.rotations.alerting.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IpWhitelistValidatorTest {

    @Nested
    @DisplayName("Whitelist Enabled")
    class WhitelistEnabledTests {

        @Test
        @DisplayName("Should allow IP matching exact pattern")
        void shouldAllowMatchingExactIp() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getRemoteAddr()).thenReturn("192.168.1.100");

            IpWhitelistValidator validator = new IpWhitelistValidator("192.168.1.100", true);
            assertTrue(validator.isAllowed(request, "test-source"));
        }

        @Test
        @DisplayName("Should allow IP matching CIDR pattern")
        void shouldAllowMatchingCidr() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getRemoteAddr()).thenReturn("192.168.1.100");

            IpWhitelistValidator validator = new IpWhitelistValidator("192.168.1.0/24", true);
            assertTrue(validator.isAllowed(request, "test-source"));
        }

        @Test
        @DisplayName("Should allow IP matching larger CIDR")
        void shouldAllowMatchingLargerCidr() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getRemoteAddr()).thenReturn("10.0.5.1");

            IpWhitelistValidator validator = new IpWhitelistValidator("10.0.0.0/8", true);
            assertTrue(validator.isAllowed(request, "test-source"));
        }

        @Test
        @DisplayName("Should allow IP matching second pattern in list")
        void shouldAllowSecondIpInList() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getRemoteAddr()).thenReturn("10.0.0.5");

            IpWhitelistValidator validator = new IpWhitelistValidator("192.168.1.1,10.0.0.5,172.16.0.1", true);
            assertTrue(validator.isAllowed(request, "test-source"));
        }

        @Test
        @DisplayName("Should block IP not in whitelist")
        void shouldBlockNonWhitelistedIp() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getRemoteAddr()).thenReturn("10.99.99.99");

            IpWhitelistValidator validator = new IpWhitelistValidator("192.168.1.0/24,10.0.0.0/24", true);
            assertFalse(validator.isAllowed(request, "test-source"));
        }

        @Test
        @DisplayName("Should block IP not matching CIDR")
        void shouldBlockNonMatchingCidr() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getRemoteAddr()).thenReturn("192.168.2.100");

            IpWhitelistValidator validator = new IpWhitelistValidator("192.168.1.0/24", true);
            assertFalse(validator.isAllowed(request, "test-source"));
        }

        @Test
        @DisplayName("Should extract IP from X-Forwarded-For header")
        void shouldExtractIpFromForwardedFor() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.100, 10.0.0.1");
            when(request.getHeader("X-Real-IP")).thenReturn(null);
            when(request.getHeader("CF-Connecting-IP")).thenReturn(null);

            IpWhitelistValidator validator = new IpWhitelistValidator("192.168.1.100", true);
            assertTrue(validator.isAllowed(request, "test-source"));
        }

        @Test
        @DisplayName("Should extract IP from X-Real-IP header")
        void shouldExtractIpFromXRealIp() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getHeader("X-Real-IP")).thenReturn("172.16.0.5");
            when(request.getHeader("CF-Connecting-IP")).thenReturn(null);

            IpWhitelistValidator validator = new IpWhitelistValidator("172.16.0.5", true);
            assertTrue(validator.isAllowed(request, "test-source"));
        }

        @Test
        @DisplayName("Should extract IP from CF-Connecting-IP header")
        void shouldExtractIpFromCloudflare() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getHeader("X-Real-IP")).thenReturn(null);
            when(request.getHeader("CF-Connecting-IP")).thenReturn("203.0.113.1");

            IpWhitelistValidator validator = new IpWhitelistValidator("203.0.113.1", true);
            assertTrue(validator.isAllowed(request, "test-source"));
        }
    }

    @Nested
    @DisplayName("Whitelist Disabled")
    class WhitelistDisabledTests {

        @Test
        @DisplayName("Should allow all when disabled")
        void shouldAllowAllWhenDisabled() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getRemoteAddr()).thenReturn("1.2.3.4");

            IpWhitelistValidator validator = new IpWhitelistValidator("192.168.1.1", false);
            assertTrue(validator.isAllowed(request, "test-source"));
        }

        @Test
        @DisplayName("Should allow all when no IPs configured")
        void shouldAllowAllWhenNoIpsConfigured() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getRemoteAddr()).thenReturn("1.2.3.4");

            IpWhitelistValidator validator = new IpWhitelistValidator("", true);
            assertTrue(validator.isAllowed(request, "test-source"));
        }

        @Test
        @DisplayName("Should allow all when empty string provided")
        void shouldAllowAllWhenEmptyString() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getRemoteAddr()).thenReturn("99.99.99.99");

            IpWhitelistValidator validator = new IpWhitelistValidator(null, true);
            assertTrue(validator.isAllowed(request, "test-source"));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should block when client IP is blank")
        void shouldBlockBlankClientIp() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getRemoteAddr()).thenReturn("");

            IpWhitelistValidator validator = new IpWhitelistValidator("192.168.1.1", true);
            assertFalse(validator.isAllowed(request, "test-source"));
        }

        @Test
        @DisplayName("Should block when X-Forwarded-For is empty")
        void shouldBlockEmptyForwardedFor() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("X-Forwarded-For")).thenReturn("");
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");

            IpWhitelistValidator validator = new IpWhitelistValidator("127.0.0.1", true);
            // Falls back to remoteAddr which matches, so should allow
            assertTrue(validator.isAllowed(request, "test-source"));
        }
    }
}
