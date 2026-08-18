package com.company.rotations.alerting.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnProperty(name = "app.cors.enabled", havingValue = "true", matchIfMissing = true)
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    @Value("${app.cors.max-age:3600}")
    private int maxAge;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins;
        if (allowedOrigins.isEmpty()) {
            origins = new String[]{"*"};
        } else {
            origins = allowedOrigins.split(",");
        }
        registry.addMapping("/api/**")
            .allowedOriginPatterns(origins)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders("X-Total-Count", "X-Page-Size")
            .allowCredentials(!"*".equals(allowedOrigins.trim()))
            .maxAge(maxAge);
    }
}
