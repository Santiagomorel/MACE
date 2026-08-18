package com.company.rotations.actionexecutor.service;

import com.company.rotations.actionexecutor.domain.RotationResult;
import com.company.rotations.actionexecutor.strategy.NotificationStrategy;
import com.company.rotations.models.Severidad;
import com.company.rotations.models.Credential;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ActionExecutor {

    ActionExecutionResult executeRotation(Credential credential, String tenantId,
                                          Severidad severity, UUID alertId);

    List<NotificationStrategy.NotificationResult> sendNotifications(String tenantId, UUID alertId,
                                                                    Severidad severity,
                                                                    String credentialId,
                                                                    List<String> notificationProfile);

    class ActionExecutionResult {
        private final boolean success;
        private final RotationResult rotationResult;
        private final List<NotificationStrategy.NotificationResult> notificationResults;
        private final String errorMessage;
        private final boolean escalated;

        public ActionExecutionResult(boolean success, RotationResult rotationResult,
                                     List<NotificationStrategy.NotificationResult> notificationResults,
                                     String errorMessage, boolean escalated) {
            this.success = success;
            this.rotationResult = rotationResult;
            this.notificationResults = notificationResults;
            this.errorMessage = errorMessage;
            this.escalated = escalated;
        }

        public static ActionExecutionResult success(RotationResult rotationResult,
                                                    List<NotificationStrategy.NotificationResult> notificationResults) {
            return new ActionExecutionResult(true, rotationResult, notificationResults, null, false);
        }

        public static ActionExecutionResult failure(String errorMessage, boolean escalated) {
            return new ActionExecutionResult(false, null, null, errorMessage, escalated);
        }

        public boolean isSuccess() { return success; }
        public RotationResult getRotationResult() { return rotationResult; }
        public List<NotificationStrategy.NotificationResult> getNotificationResults() { return notificationResults; }
        public String getErrorMessage() { return errorMessage; }
        public boolean isEscalated() { return escalated; }
    }
}
