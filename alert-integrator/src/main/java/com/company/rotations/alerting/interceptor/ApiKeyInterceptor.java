package com.company.rotations.alerting.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyInterceptor.class);
    private static final String API_KEY_HEADER = "X-API-Key";

    private final String apiKey;
    private final boolean enforceApiKey;

    public ApiKeyInterceptor(
            @Value("${app.admin.api-key:}") String apiKey,
            @Value("${app.admin.api-key-enforce:true}") boolean enforceApiKey) {
        this.apiKey = apiKey;
        this.enforceApiKey = enforceApiKey;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!enforceApiKey) {
            return true;
        }

        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/admin")) {
            return true;
        }

        String providedKey = request.getHeader(API_KEY_HEADER);

        if (providedKey == null || providedKey.isBlank()) {
            logger.warn("Missing API key for admin endpoint: {}", path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"MISSING_API_KEY\",\"message\":\"X-API-Key header is required\"}");
            return false;
        }

        if (!isValidKey(providedKey)) {
            logger.warn("Invalid API key provided for admin endpoint: {}", path);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"INVALID_API_KEY\",\"message\":\"Invalid API key\"}");
            return false;
        }

        return true;
    }

    private boolean isValidKey(String providedKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        return apiKey.equals(providedKey);
    }
}
