package com.company.rotations.spi;

import com.company.rotations.models.RotationAction;
import java.util.Map;

public interface RotationService {
    String VERSION = "1.0.0";

    RotationAction rotate(String credentialType, Map<String, String> credentials, String tenantId);

    default String getVersion() {
        return VERSION;
    }
}
