package com.company.rotations.alerting.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Component
public class SignatureValidator {

    private static final Logger logger = LoggerFactory.getLogger(SignatureValidator.class);
    private static final String ALGORITHM = "HmacSHA256";

    private final String sharedSecret;
    private final String signatureHeader;

    public SignatureValidator(
            @Value("${app.providers.gitguardian.shared-secret:test-secret}") String sharedSecret,
            @Value("${app.providers.gitguardian.signature-header:X-GitGuardian-Signature}") String signatureHeader) {
        this.sharedSecret = sharedSecret;
        this.signatureHeader = signatureHeader;
    }

    public boolean isValid(String payload, String signature, String source) {
        if (signature == null || signature.isBlank()) {
            logger.warn("Missing signature header for source: {}", source);
            return false;
        }

        if (sharedSecret == null || sharedSecret.isBlank() || "changeme".equals(sharedSecret)) {
            logger.warn("Shared secret not configured, skipping signature validation for source: {}", source);
            return true;
        }

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    sharedSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(keySpec);

            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedSignature = HexFormat.of().formatHex(hmacBytes);

            boolean valid = constantTimeEquals(computedSignature, signature.toLowerCase());
            if (valid) {
                logger.info("Signature validation passed for source: {}", source);
            } else {
                logger.warn("Signature validation failed for source: {}, computed={}, received={}",
                        source, truncate(computedSignature), truncate(signature));
            }
            return valid;
        } catch (Exception e) {
            logger.error("Error computing signature for source: {}", source, e);
            return false;
        }
    }

    public String getSignatureHeaderName() {
        return signatureHeader;
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 16) return value;
        return value.substring(0, 8) + "..." + value.substring(value.length() - 8);
    }
}
