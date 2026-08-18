package com.company.rotations.spi;

import com.company.rotations.models.Severidad;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PlaybookManager {
    String VERSION = "1.0.0";

    Playbook loadPlaybook(String credentialType);

    Playbook loadPlaybookByPlaybookId(String playbookId);

    List<String> getPlaybookSteps(String credentialType);

    List<Severidad> getSeverityFloor(String playbookId);

    boolean validatePlaybook(Playbook playbook);

    default String getVersion() {
        return VERSION;
    }

    interface Playbook {
        String getPlaybookId();
        String getVersion();
        List<String> getCredentialTypes();
        Map<String, Severidad> getSeverityFloor();
        AutoRotateConfig getAutoRotate();
        List<ActionOnExposure> getActionsOnExposure();
        List<ComplianceTag> getComplianceTags();
        List<CredentialTargeted> getCredentialsTargeted();
        Conditions getConditions();
        boolean canLowerFloor();

        interface AutoRotateConfig {
            boolean isEnabled();
            Integer getMaxWindowMins();
        }

        interface ActionOnExposure {
            String getActionType();
            String getTarget();
            int getPriorityOrder();
        }

        interface ComplianceTag {
            String getSource();
            String getControlDescription();
        }

        interface CredentialTargeted {
            String getCredentialType();
            String getDescription();
        }

        interface Conditions {
            String getProvider();
            String getDetectionSource();
        }
    }
}
