package com.company.rotations.verification.provider;

import com.company.rotations.verification.model.ProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class ProviderDetector {

    private static final Logger logger = LoggerFactory.getLogger(ProviderDetector.class);

    private static final String AWS_PREFIX = "AKIA";
    private static final String AZURE_PREFIX = "eyJ";
    private static final String GCP_PREFIX = "AIzaSy";

    public ProviderType detectProvider(String providerNameFromAlert, String credentialValue) {
        if (providerNameFromAlert != null && !providerNameFromAlert.isBlank()) {
            ProviderType detected = resolveProviderType(providerNameFromAlert);
            logger.debug("Provider detected from alert source: {}", detected);
            return detected;
        }

        if (credentialValue != null && !credentialValue.isBlank()) {
            ProviderType heuristic = detectByHeuristic(credentialValue);
            if (heuristic != ProviderType.UNKNOWN) {
                logger.debug("Provider detected via heuristic prefix: {}", heuristic);
                return heuristic;
            }
        }

        logger.warn("Could not detect provider from alert or credential value");
        return ProviderType.UNKNOWN;
    }

    private ProviderType resolveProviderType(String providerName) {
        String normalized = providerName.toLowerCase().trim();
        if ("aws".equals(normalized) || normalized.contains("amazon") || normalized.contains("sts")) {
            return ProviderType.AWS;
        }
        if ("azure".equals(normalized) || normalized.contains("azure") || normalized.contains("aad") || normalized.contains("activedirectory")) {
            return ProviderType.AZURE;
        }
        if ("gcp".equals(normalized) || normalized.contains("google") || normalized.contains("googlecloud")) {
            return ProviderType.GCP;
        }
        return ProviderType.UNKNOWN;
    }

    public ProviderType detectByHeuristic(String credentialValue) {
        if (credentialValue == null || credentialValue.isBlank()) {
            return ProviderType.UNKNOWN;
        }

        if (credentialValue.startsWith(AWS_PREFIX)) {
            return ProviderType.AWS;
        }
        if (credentialValue.startsWith(AZURE_PREFIX)) {
            return ProviderType.AZURE;
        }
        if (credentialValue.startsWith(GCP_PREFIX)) {
            return ProviderType.GCP;
        }

        return ProviderType.UNKNOWN;
    }
}
