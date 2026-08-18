package com.company.rotations.verification.provider;

import com.company.rotations.logging.service.AuditService;
import com.company.rotations.models.AlertType;
import com.company.rotations.models.VerificationResult;
import com.company.rotations.spi.VerificationProvider;
import com.company.rotations.verification.enumeration.PermissionEnumerator;
import com.company.rotations.verification.model.PermissionMatrix;
import com.company.rotations.verification.severity.SeverityRuleEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;
import software.amazon.awssdk.services.sts.model.ExpiredTokenException;

import java.util.Map;
import java.util.UUID;

@Component
public class AwsStsVerificationProvider implements VerificationProvider {

    private static final Logger logger = LoggerFactory.getLogger(AwsStsVerificationProvider.class);

    private final StsClient stsClient;
    private final IamClient iamClient;
    private final PermissionEnumerator permissionEnumerator;
    private final SeverityRuleEngine severityRuleEngine;
    private final AuditService auditService;

    public AwsStsVerificationProvider(StsClient stsClient,
                                      IamClient iamClient,
                                      PermissionEnumerator permissionEnumerator,
                                      SeverityRuleEngine severityRuleEngine,
                                      AuditService auditService) {
        this.stsClient = stsClient;
        this.iamClient = iamClient;
        this.permissionEnumerator = permissionEnumerator;
        this.severityRuleEngine = severityRuleEngine;
        this.auditService = auditService;
    }

    private AlertType resolveAlertType(String credentialType) {
        if (credentialType == null) {
            return AlertType.GENERIC;
        }
        String normalized = credentialType.toUpperCase().trim();
        if (normalized.contains("ACCESS_KEY") || normalized.contains("AWS")) {
            return AlertType.AWS_ACCESS_KEY;
        }
        if (normalized.contains("IAM")) {
            return AlertType.IAM_USER;
        }
        if (normalized.contains("RDS") || normalized.contains("DATABASE")) {
            return AlertType.RDS_CREDENTIAL;
        }
        return AlertType.GENERIC;
    }

    @Override
    public VerificationResult verify(String credentialType,
                                     Map<String, String> credentials,
                                     String tenantId) {
        String accessKey = credentials != null ? credentials.get("accessKey") : null;
        String secretKey = credentials != null ? credentials.get("secretKey") : null;

        logger.info("AWS STS verification starting for credential type: {}", credentialType);

        Map<String, Object> verificationEventData = new java.util.HashMap<>();
        verificationEventData.put("provider", "aws");
        verificationEventData.put("credential_type", credentialType);
        verificationEventData.put("tenant_id", tenantId);

        try {
            auditService.logVerificationStarted(verificationEventData);
        } catch (Exception e) {
            logger.warn("Could not log verification started audit: {}", e.getMessage());
        }

        AlertType alertType = resolveAlertType(credentialType);

        if (accessKey == null || secretKey == null) {
            logger.warn("Missing AWS credentials for verification");
            return new VerificationResult(
                    UUID.randomUUID(), false, "Missing access key or secret key",
                    "LOW", null, alertType, tenantId, "aws");
        }

        try {
            GetCallerIdentityResponse response = stsClient.getCallerIdentity(GetCallerIdentityRequest.builder().build());

            logger.info("AWS STS verification successful - AccountId={}, Arn={}",
                    response.account(), response.arn());

            String identityArn = response.arn();
            String accountId = response.account();

            PermissionMatrix permissionMatrix = new PermissionMatrix();
            if (identityArn != null && !identityArn.contains("root")) {
                permissionMatrix = permissionEnumerator.enumeratePermissions(iamClient, identityArn);
            }

            String calculatedSeverity = calculateSeverity(permissionMatrix);
            SeverityRuleEngine.Severity effectiveSeverity = severityRuleEngine.applyFloor(
                    tenantId, SeverityRuleEngine.Severity.valueOf(calculatedSeverity));

            Map<String, Object> completedEventData = new java.util.HashMap<>();
            completedEventData.put("provider", "aws");
            completedEventData.put("account_id", accountId);
            completedEventData.put("status", "VERIFIED");
            completedEventData.put("severity", effectiveSeverity.name());
            completedEventData.put("permission_count", permissionMatrix.size());

            try {
                auditService.logVerificationCompleted(completedEventData);
            } catch (Exception e) {
                logger.warn("Could not log verification completed audit: {}", e.getMessage());
            }

            return new VerificationResult(
                    UUID.randomUUID(), true, "AWS STS GetCallerIdentity successful",
                    effectiveSeverity.name(),
                    calculateBlastRadius(permissionMatrix),
                    alertType, tenantId, "aws");

        } catch (ExpiredTokenException e) {
            logger.warn("AWS credentials expired for credential type: {}", credentialType);
            return new VerificationResult(
                    UUID.randomUUID(), false, "AWS credentials expired: " + e.getMessage(),
                    "LOW", null, alertType, tenantId, "aws");

        } catch (Exception e) {
            logger.error("Unexpected error during AWS STS verification: {}", e.getMessage(), e);
            return new VerificationResult(
                    UUID.randomUUID(), false, "Verification failed: " + e.getMessage(),
                    "HIGH", null, alertType, tenantId, "aws");
        }
    }

    private String calculateSeverity(PermissionMatrix matrix) {
        if (matrix.isEmpty()) {
            return "LOW";
        }

        java.util.Set<String> allowedActions = matrix.getAllowedActions();

        if (allowedActions.stream().anyMatch(a -> a.equals("*") || a.contains("iam:*"))) {
            return "CRITICAL";
        }

        if (allowedActions.stream().anyMatch(a -> a.contains("iam:Delete") || a.contains("iam:Create"))) {
            return "HIGH";
        }

        if (allowedActions.size() > 20) {
            return "MEDIUM";
        }

        return "LOW";
    }

    private String calculateBlastRadius(PermissionMatrix matrix) {
        if (matrix.isEmpty()) {
            return "NONE";
        }

        java.util.Set<String> actions = matrix.getAllowedActions();

        if (actions.contains("*") || actions.stream().anyMatch(a -> a.contains("iam:*"))) {
            return "CRITICAL";
        }

        if (actions.stream().anyMatch(a -> a.contains("iam:Create") || a.contains("iam:Delete"))) {
            return "HIGH";
        }

        if (actions.size() > 10) {
            return "MEDIUM";
        }

        return "LOW";
    }
}
