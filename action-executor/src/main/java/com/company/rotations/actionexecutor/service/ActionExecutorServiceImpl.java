package com.company.rotations.actionexecutor.service;

import com.company.rotations.actionexecutor.audit.RotationTransitionDto;
import com.company.rotations.actionexecutor.domain.RotationResult;
import com.company.rotations.actionexecutor.domain.RotationStateMachine;
import com.company.rotations.actionexecutor.domain.RotationState;
import com.company.rotations.logging.service.AuditService;
import com.company.rotations.actionexecutor.strategy.NotificationDispatcherStrategy;
import com.company.rotations.actionexecutor.strategy.NotificationStrategy;
import com.company.rotations.models.Severidad;
import com.company.rotations.models.Credential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ActionExecutorServiceImpl implements ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(ActionExecutorServiceImpl.class);
    private static final long GLOBAL_TIMEOUT_MS = 5 * 60 * 1000L; // 5 minutes

    private final AwsRotationService awsRotationService;
    private final NotificationDispatcherStrategy notificationDispatcher;
    private final AuditTrailService auditTrailService;
    private final AuditService auditService;

    public ActionExecutorServiceImpl(AwsRotationService awsRotationService,
                                      NotificationDispatcherStrategy notificationDispatcher,
                                      AuditTrailService auditTrailService,
                                      AuditService auditService) {
        this.awsRotationService = awsRotationService;
        this.notificationDispatcher = notificationDispatcher;
        this.auditTrailService = auditTrailService;
        this.auditService = auditService;
    }

    @Override
    public ActionExecutionResult executeRotation(Credential credential, String tenantId,
                                                  Severidad severity, UUID alertId) {
        log.info("Starting action execution pipeline for alert {} tenant {} severity {}",
                alertId, tenantId, severity);

        Map<String, Object> actionEventData = Map.of(
                "alert_id", alertId.toString(),
                "tenant_id", tenantId,
                "severity", severity.name(),
                "credential_id", credential.getKeyId(),
                "credential_type", credential.getCredentialType() != null ? credential.getCredentialType() : "unknown"
        );

        try {
            auditService.logActionExecuted(actionEventData);
        } catch (Exception e) {
            log.warn("Could not log action executed audit: {}", e.getMessage());
        }

        RotationStateMachine stateMachine = new RotationStateMachine(
                UUID.randomUUID().toString(), alertId
        );
        stateMachine.setStartTime(Instant.now());

        ActionExecutionResult result = executeWithTimeout(
                credential, tenantId, severity, alertId, stateMachine
        );

        if (result.isEscalated()) {
            auditTrailService.logEscalation(tenantId, alertId, severity,
                    result.getErrorMessage(), 3);
        }

        try {
            auditService.logActionExecuted(Map.of(
                    "alert_id", alertId.toString(),
                    "tenant_id", tenantId,
                    "severity", severity.name(),
                    "credential_id", credential.getKeyId(),
                    "success", result.isSuccess(),
                    "escalated", result.isEscalated(),
                    "message", result.getErrorMessage()
            ));
        } catch (Exception e) {
            log.warn("Could not log action completed audit: {}", e.getMessage());
        }

        log.info("Action execution pipeline completed for alert {}: success={}, escalated={}",
                alertId, result.isSuccess(), result.isEscalated());

        return result;
    }

    @Override
    public List<NotificationStrategy.NotificationResult> sendNotifications(String tenantId,
                                                                              UUID alertId,
                                                                              Severidad severity,
                                                                              String credentialId,
                                                                              List<String> notificationProfile) {
        return notificationDispatcher.dispatchNotifications(
                tenantId, alertId, severity, credentialId, notificationProfile
        );
    }

    private ActionExecutionResult executeWithTimeout(Credential credential, String tenantId,
                                                      Severidad severity, UUID alertId,
                                                      RotationStateMachine stateMachine) {
        final boolean[] timedOut = {false};

        Thread[] timeoutThreadHolder = new Thread[1];

        try {
            Thread timeoutThread = new Thread(() -> {
                try {
                    Thread.sleep(GLOBAL_TIMEOUT_MS);
                    if (!stateMachine.isTerminalState()) {
                        timedOut[0] = true;
                        log.error("Global timeout reached for alert {}. Cancelling rotation.", alertId);

                        RotationTransitionDto timeoutTransition = stateMachine.timeoutTransition();
                        auditTrailService.logRotationTransition(timeoutTransition);
                        auditTrailService.logTimeout(tenantId, alertId, severity);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            timeoutThreadHolder[0] = timeoutThread;
            timeoutThread.setDaemon(true);
            timeoutThread.start();

            RotationResult rotationResult = awsRotationService.executeRotation(
                    credential, tenantId, stateMachine, severity
            );

            timeoutThread.interrupt();

            if (timedOut[0]) {
                return ActionExecutionResult.failure(
                        "Rotation timed out after 5 minutes", true
                );
            }

            if (rotationResult.isSuccess()) {
                List<NotificationStrategy.NotificationResult> notifications =
                        sendNotificationsAsync(tenantId, alertId, severity,
                                credential.getKeyId());

                return ActionExecutionResult.success(rotationResult, notifications);
            } else {
                return ActionExecutionResult.failure(
                        rotationResult.getErrorMessage(), true
                );
            }

        } catch (Exception e) {
            Thread timeoutThread = timeoutThreadHolder[0];
            if (timeoutThread != null && !timedOut[0]) {
                timeoutThread.interrupt();
            }
            log.error("Unexpected error in execution pipeline: {}", e.getMessage());
            return ActionExecutionResult.failure("Unexpected error: " + e.getMessage(), false);
        }
    }

    private List<NotificationStrategy.NotificationResult> sendNotificationsAsync(
            String tenantId, UUID alertId, Severidad severity, String credentialId) {

        try {
            return notificationDispatcher.dispatchNotificationsAsync(
                    tenantId, alertId, severity, credentialId,
                    List.of("slack", "email")
            );
        } catch (Exception e) {
            log.warn("Failed to send async notifications: {}", e.getMessage());
            return notificationDispatcher.dispatchNotifications(
                    tenantId, alertId, severity, credentialId,
                    List.of("slack")
            );
        }
    }
}
