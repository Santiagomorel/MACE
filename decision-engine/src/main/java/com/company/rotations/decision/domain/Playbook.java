package com.company.rotations.decision.domain;

import com.company.rotations.models.Severidad;
import java.util.List;
import java.util.Map;

public class Playbook {

    private String playbookId;
    private String version;
    private List<String> credentialTypes;
    private Map<String, Severidad> severityFloor;
    private AutoRotate autoRotate;
    private List<ActionOnExposure> actionsOnExposure;
    private List<ComplianceTag> complianceTags;
    private List<CredentialTargeted> credentialsTargeted;
    private Conditions conditions;
    private Boolean canLowerFloor;

    public String getPlaybookId() { return playbookId; }
    public void setPlaybookId(String playbookId) { this.playbookId = playbookId; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public List<String> getCredentialTypes() { return credentialTypes; }
    public void setCredentialTypes(List<String> credentialTypes) { this.credentialTypes = credentialTypes; }

    public Map<String, Severidad> getSeverityFloor() { return severityFloor; }
    public void setSeverityFloor(Map<String, Severidad> severityFloor) { this.severityFloor = severityFloor; }

    public AutoRotate getAutoRotate() { return autoRotate; }
    public void setAutoRotate(AutoRotate autoRotate) { this.autoRotate = autoRotate; }

    public List<ActionOnExposure> getActionsOnExposure() { return actionsOnExposure; }
    public void setActionsOnExposure(List<ActionOnExposure> actionsOnExposure) { this.actionsOnExposure = actionsOnExposure; }

    public List<ComplianceTag> getComplianceTags() { return complianceTags; }
    public void setComplianceTags(List<ComplianceTag> complianceTags) { this.complianceTags = complianceTags; }

    public List<CredentialTargeted> getCredentialsTargeted() { return credentialsTargeted; }
    public void setCredentialsTargeted(List<CredentialTargeted> credentialsTargeted) { this.credentialsTargeted = credentialsTargeted; }

    public Conditions getConditions() { return conditions; }
    public void setConditions(Conditions conditions) { this.conditions = conditions; }

    public Boolean getCanLowerFloor() { return canLowerFloor; }
    public void setCanLowerFloor(Boolean canLowerFloor) { this.canLowerFloor = canLowerFloor; }

    public static class AutoRotate {
        private Boolean enabled;
        private Integer maxWindowMins;

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }

        public Integer getMaxWindowMins() { return maxWindowMins; }
        public void setMaxWindowMins(Integer maxWindowMins) { this.maxWindowMins = maxWindowMins; }
    }

    public static class ActionOnExposure {
        private String actionType;
        private String target;
        private Integer priorityOrder;

        public String getActionType() { return actionType; }
        public void setActionType(String actionType) { this.actionType = actionType; }

        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }

        public Integer getPriorityOrder() { return priorityOrder; }
        public void setPriorityOrder(Integer priorityOrder) { this.priorityOrder = priorityOrder; }
    }

    public static class ComplianceTag {
        private String source;
        private String controlDescription;

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public String getControlDescription() { return controlDescription; }
        public void setControlDescription(String controlDescription) { this.controlDescription = controlDescription; }
    }

    public static class CredentialTargeted {
        private String credentialType;
        private String description;

        public String getCredentialType() { return credentialType; }
        public void setCredentialType(String credentialType) { this.credentialType = credentialType; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class Conditions {
        private String provider;
        private String detectionSource;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public String getDetectionSource() { return detectionSource; }
        public void setDetectionSource(String detectionSource) { this.detectionSource = detectionSource; }
    }
}
