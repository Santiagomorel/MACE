package com.company.rotations.verification.service;

import com.company.rotations.logging.service.AuditService;
import com.company.rotations.verification.adapter.AlertInputAdapter;
import com.company.rotations.verification.model.CredentialAlert;
import com.company.rotations.verification.model.VerificationResult;
import com.company.rotations.verification.model.VerificationStatus;
import com.company.rotations.verification.validator.CredentialValidatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class VerificationEngineService {

    private static final Logger logger = LoggerFactory.getLogger(VerificationEngineService.class);

    private final AlertInputAdapter inputAdapter;
    private final CredentialValidatorService credentialValidator;
    private final AuditService auditService;

    public VerificationEngineService(AlertInputAdapter inputAdapter,
                                       CredentialValidatorService credentialValidator,
                                       AuditService auditService) {
        this.inputAdapter = inputAdapter;
        this.credentialValidator = credentialValidator;
        this.auditService = auditService;
    }

    public VerificationResult processAlert(Map<String, Object> rawAlertPayload,
                                             List<String> knownAccountIds) {
        logger.info("VerificationEngine: processing incoming alert");

        CredentialAlert alert = inputAdapter.toCredentialAlert(rawAlertPayload);

        logger.info("VerificationEngine: alert processed - eventId={}, credentialId={}",
                alert.getEventId(), alert.getCredentialValueHash());

        logger.info("VerificationEngine: starting credential validation for eventId={}", alert.getEventId());

        Map<String, Object> verificationEventData = new HashMap<>();
        verificationEventData.put("event_id", alert.getEventId());
        verificationEventData.put("credential_id", alert.getCredentialValueHash());
        verificationEventData.put("provider", alert.getProviderName() != null ? alert.getProviderName() : "unknown");

        try {
            auditService.logVerificationStarted(verificationEventData);
        } catch (Exception e) {
            logger.warn("Could not log verification started audit: {}", e.getMessage());
        }

        VerificationResult result = credentialValidator.verifyCredential(
                alert.getAccountHint(),
                alert.getCredentialValue(),
                alert.getCredentialValueHash(),
                rawAlertPayload,
                knownAccountIds
        );

        logger.info("VerificationEngine: verification complete - eventId={}, status={}, accountId={}, actionMatrixSize={}",
                alert.getEventId(),
                result.getStatus(),
                result.getAccountId(),
                result.getActionMatrix() != null ? result.getActionMatrix().size() : 0);

        logger.info("VerificationEngine: output ready for Drools evaluation - account_id={}, identity_arn={}, action_matrix={}, last_used_date={}",
                result.getAccountId(),
                result.getIdentityArn(),
                result.getActionMatrix(),
                result.getLastUsedDate());

        try {
            Map<String, Object> completedEventData = new HashMap<>();
            completedEventData.put("event_id", alert.getEventId());
            completedEventData.put("account_id", result.getAccountId());
            completedEventData.put("status", result.getStatus());
            completedEventData.put("action_matrix_size", result.getActionMatrix() != null ? result.getActionMatrix().size() : 0);
            completedEventData.put("identity_arn", result.getIdentityArn());
            completedEventData.put("success", result.getStatus() == VerificationStatus.VERIFIED);
            auditService.logVerificationCompleted(completedEventData);
        } catch (Exception e) {
            logger.warn("Could not log verification completed audit: {}", e.getMessage());
        }

        return result;
    }
}
