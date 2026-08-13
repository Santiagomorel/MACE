package com.company.rotations.logging.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MdcLoggingFilterTest {

    private MdcLoggingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new MdcLoggingFilter();
        MDC.clear();
    }

    @Test
    void doFilterInternal_generatesTraceIdWhenNotPresent()
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] capturedTraceId = new String[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request,
                                 jakarta.servlet.ServletResponse response) {
                capturedTraceId[0] = MDC.get("trace_id");
            }
        };

        filter.doFilterInternal(request, response, chain);

        assertNotNull(capturedTraceId[0]);
        assertDoesNotThrow(() -> UUID.fromString(capturedTraceId[0]));
    }

    @Test
    void doFilterInternal_usesExistingTraceIdFromHeader()
            throws ServletException, IOException {
        String existingTraceId = "custom-trace-12345";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", existingTraceId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] capturedTraceId = new String[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request,
                                 jakarta.servlet.ServletResponse response) {
                capturedTraceId[0] = MDC.get("trace_id");
            }
        };

        filter.doFilterInternal(request, response, chain);

        assertEquals(existingTraceId, capturedTraceId[0]);
    }

    @Test
    void doFilterInternal_generatesAlertIdWhenNotInPayload()
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] capturedAlertId = new String[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request,
                                 jakarta.servlet.ServletResponse response) {
                capturedAlertId[0] = MDC.get("alert_id");
            }
        };

        filter.doFilterInternal(request, response, chain);

        assertNotNull(capturedAlertId[0]);
        assertDoesNotThrow(() -> UUID.fromString(capturedAlertId[0]));
    }

    @Test
    void doFilterInternal_extractsAlertIdFromSourceEventId()
            throws ServletException, IOException {
        String sourceEventId = "alert-xyz-789";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("sourceEventId", sourceEventId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] capturedAlertId = new String[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request,
                                 jakarta.servlet.ServletResponse response) {
                capturedAlertId[0] = MDC.get("alert_id");
            }
        };

        filter.doFilterInternal(request, response, chain);

        assertEquals(sourceEventId, capturedAlertId[0]);
    }

    @Test
    void doFilterInternal_extractsAlertIdFromHeader()
            throws ServletException, IOException {
        String sourceEventId = "alert-from-header-456";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Source-Event-Id", sourceEventId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] capturedAlertId = new String[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request,
                                 jakarta.servlet.ServletResponse response) {
                capturedAlertId[0] = MDC.get("alert_id");
            }
        };

        filter.doFilterInternal(request, response, chain);

        assertEquals(sourceEventId, capturedAlertId[0]);
    }

    @Test
    void doFilterInternal_extractsClientIdFromHeader()
            throws ServletException, IOException {
        String clientId = "client-abc-123";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Client-Id", clientId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] capturedClientId = new String[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request,
                                 jakarta.servlet.ServletResponse response) {
                capturedClientId[0] = MDC.get("client_id");
            }
        };

        filter.doFilterInternal(request, response, chain);

        assertEquals(clientId, capturedClientId[0]);
    }

    @Test
    void doFilterInternal_extractsClientIdFromTenantHeader()
            throws ServletException, IOException {
        String clientId = "tenant-xyz-456";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", clientId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] capturedClientId = new String[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request,
                                 jakarta.servlet.ServletResponse response) {
                capturedClientId[0] = MDC.get("client_id");
            }
        };

        filter.doFilterInternal(request, response, chain);

        assertEquals(clientId, capturedClientId[0]);
    }

    @Test
    void doFilterInternal_clearsMdcOnSuccess()
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "test-trace");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNull(MDC.get("trace_id"));
        assertNull(MDC.get("alert_id"));
        assertNull(MDC.get("client_id"));
        assertNull(MDC.get("phase"));
        assertNull(MDC.get("step"));
    }

    @Test
    void doFilterInternal_clearsMdcOnException()
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "test-trace");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request,
                                 jakarta.servlet.ServletResponse response)
                    throws IOException, ServletException {
                throw new ServletException("Test exception");
            }
        };

        assertThrows(ServletException.class,
            () -> filter.doFilterInternal(request, response, chain));

        assertNull(MDC.get("trace_id"));
        assertNull(MDC.get("alert_id"));
        assertNull(MDC.get("client_id"));
        assertNull(MDC.get("phase"));
        assertNull(MDC.get("step"));
    }

    @Test
    void getOrder_returnsNegativeValue() {
        assertTrue(filter.getOrder() < 0);
    }
}
