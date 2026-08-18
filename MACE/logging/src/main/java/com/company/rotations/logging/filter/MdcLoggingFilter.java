package com.company.rotations.logging.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class MdcLoggingFilter extends OncePerRequestFilter implements Ordered {

    private static final String HEADER_TRACE_ID = "X-Trace-Id";
    private static final String FIELD_TRACE_ID = "trace_id";
    private static final String FIELD_ALERT_ID = "alert_id";
    private static final String FIELD_CLIENT_ID = "client_id";
    private static final String FIELD_PHASE = "phase";
    private static final String FIELD_STEP = "step";

    private static final String[] MDC_FIELDS = {
        FIELD_TRACE_ID, FIELD_ALERT_ID, FIELD_CLIENT_ID, FIELD_PHASE, FIELD_STEP
    };

    @Override
    public int getOrder() {
        return -1;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            populateMdc(request);
            filterChain.doFilter(request, response);
        } finally {
            clearMdc();
        }
    }

    private void populateMdc(HttpServletRequest request) {
        String traceId = request.getHeader(HEADER_TRACE_ID);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString();
        }
        MDC.put(FIELD_TRACE_ID, traceId);

        String sourceEventId = extractSourceEventId(request);
        if (sourceEventId != null && !sourceEventId.isEmpty()) {
            MDC.put(FIELD_ALERT_ID, sourceEventId);
        } else {
            MDC.put(FIELD_ALERT_ID, UUID.randomUUID().toString());
        }

        String clientId = extractClientId(request);
        if (clientId != null && !clientId.isEmpty()) {
            MDC.put(FIELD_CLIENT_ID, clientId);
        }
    }

    private String extractSourceEventId(HttpServletRequest request) {
        String sourceEventId = request.getParameter("sourceEventId");
        if (sourceEventId != null && !sourceEventId.isEmpty()) {
            return sourceEventId;
        }
        return request.getHeader("X-Source-Event-Id");
    }

    private String extractClientId(HttpServletRequest request) {
        String clientId = request.getHeader("X-Client-Id");
        if (clientId != null && !clientId.isEmpty()) {
            return clientId;
        }
        clientId = request.getHeader("X-Tenant-Id");
        if (clientId != null && !clientId.isEmpty()) {
            return clientId;
        }
        return null;
    }

    private void clearMdc() {
        for (String field : MDC_FIELDS) {
            MDC.remove(field);
        }
    }
}
