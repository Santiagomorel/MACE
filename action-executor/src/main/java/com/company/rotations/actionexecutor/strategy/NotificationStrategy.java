package com.company.rotations.actionexecutor.strategy;

import com.company.rotations.models.Severidad;
import java.util.Map;
import java.util.UUID;

public interface NotificationStrategy {

    String getChannelName();

    NotificationResult send(String message, SeverityContext context);

    class SeverityContext {
        private final String tenantId;
        private final UUID alertId;
        private final Severidad severity;
        private final String credentialId;
        private final Map<String, String> channelConfig;

        public SeverityContext(String tenantId, UUID alertId, Severidad severity,
                               String credentialId, Map<String, String> channelConfig) {
            this.tenantId = tenantId;
            this.alertId = alertId;
            this.severity = severity;
            this.credentialId = credentialId;
            this.channelConfig = channelConfig != null ? channelConfig : Map.of();
        }

        public String getTenantId() { return tenantId; }
        public UUID getAlertId() { return alertId; }
        public Severidad getSeverity() { return severity; }
        public String getCredentialId() { return credentialId; }
        public Map<String, String> getChannelConfig() { return channelConfig; }
    }

    class NotificationResult {
        private final boolean success;
        private final String channel;
        private final String message;
        private final String errorMessage;

        public NotificationResult(boolean success, String channel, String message) {
            this.success = success;
            this.channel = channel;
            this.message = message;
            this.errorMessage = null;
        }

        public NotificationResult(boolean success, String channel, String message,
                                  String errorMessage) {
            this.success = success;
            this.channel = channel;
            this.message = message;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() { return success; }
        public String getChannel() { return channel; }
        public String getMessage() { return message; }
        public String getErrorMessage() { return errorMessage; }
    }
}
