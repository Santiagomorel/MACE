package com.company.rotations.spi;

import com.company.rotations.models.VerificationResult;
import java.util.Map;

public interface VerificationProvider {
    String VERSION = "1.0.0";

    VerificationResult verify(String credentialType, Map<String, String> credentials, String tenantId);

    default String getVersion() {
        return VERSION;
    }
}
