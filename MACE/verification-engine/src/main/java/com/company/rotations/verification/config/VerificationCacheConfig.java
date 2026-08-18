package com.company.rotations.verification.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Ticker;
import com.company.rotations.verification.model.VerificationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class VerificationCacheConfig {

    @Value("${app.verification.cache.ttl-minutes:5}")
    private int cacheTtlMinutes;

    @Value("${app.verification.cache.maximum-size:10000}")
    private int cacheMaxSize;

    @Bean
    public Cache<String, VerificationResult> verificationResultCache(Ticker ticker) {
        return Caffeine.newBuilder()
                .maximumSize(cacheMaxSize)
                .expireAfterWrite(Duration.ofMinutes(cacheTtlMinutes))
                .recordStats()
                .ticker(ticker)
                .build();
    }

    @Bean
    public Ticker verificationTicker() {
        return Ticker.systemTicker();
    }
}
