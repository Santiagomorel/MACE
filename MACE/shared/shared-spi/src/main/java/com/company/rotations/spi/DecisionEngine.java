package com.company.rotations.spi;

import com.company.rotations.models.Severidad;
import java.util.Map;
import java.util.UUID;

public interface DecisionEngine {

    interface DecisionResult {
        Severidad getSeverity();
        String getRationale();
        String getPlaybookId();
        String getCalculatedVia();
        Map<String, Object> getComplianceTags();
        Integer getEvaluatedRuleVersion();
    }

    DecisionResult evaluate(UUID alertId, String tenantId, String credentialType,
                            Map<String, Object> actionMatrix,
                            Map<String, Object> metadata);

    interface DecisionInput {
        UUID getAlertId();
        String getTenantId();
        String getCredentialType();
        Map<String, Object> getActionMatrix();
        Map<String, Object> getMetadata();
    }
}
