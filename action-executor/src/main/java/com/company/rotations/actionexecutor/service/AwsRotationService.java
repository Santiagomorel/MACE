package com.company.rotations.actionexecutor.service;

import com.company.rotations.actionexecutor.audit.RotationTransitionDto;
import com.company.rotations.actionexecutor.domain.RotationResult;
import com.company.rotations.actionexecutor.domain.RotationStateMachine;
import com.company.rotations.actionexecutor.domain.RotationState;
import com.company.rotations.models.Credential;
import com.company.rotations.models.Severidad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.ListAccessKeysRequest;
import software.amazon.awssdk.services.iam.model.ListAccessKeysResponse;
import software.amazon.awssdk.services.iam.model.NoSuchEntityException;
import software.amazon.awssdk.services.iam.model.UpdateAccessKeyRequest;
import software.amazon.awssdk.services.iam.model.CreateAccessKeyRequest;
import software.amazon.awssdk.services.iam.model.CreateAccessKeyResponse;
import software.amazon.awssdk.services.sts.StsClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AwsRotationService {

    private static final Logger log = LoggerFactory.getLogger(AwsRotationService.class);
    private static final int IAM_PROPAGATION_WAIT_SECONDS = 60;
    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_BACKOFF_SECONDS = {10, 30, 60};
    private static final String STATUS_ACTIVE = "Active";
    private static final String STATUS_INACTIVE = "Inactive";

    private final StsClient stsClient;
    private final IamClient iamClient;
    private final VaultService vaultService;
    private final AuditTrailService auditTrailService;

    public AwsRotationService(StsClient stsClient, IamClient iamClient,
                              VaultService vaultService, AuditTrailService auditTrailService) {
        this.stsClient = stsClient;
        this.iamClient = iamClient;
        this.vaultService = vaultService;
        this.auditTrailService = auditTrailService;
    }

    public RotationResult executeRotation(Credential credential, String tenantId,
                                          RotationStateMachine stateMachine,
                                          Severidad severity) {
        RotationResult result = new RotationResult();
        result.setAlertId(credential.getId());
        result.setStartTime(Instant.now());
        result.setAttempts(0);

        try {
            RotationTransitionDto transition = stateMachine.transitionTo(RotationState.ROTATING,
                    "Starting credential rotation for " + credential.getKeyId());
            auditTrailService.logRotationTransition(transition);

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                result.setAttempts(attempt);
                log.info("Rotation attempt {}/{} for credential {} tenant {}",
                        attempt, MAX_RETRIES, credential.getKeyId(), tenantId);

                try {
                    return executeSingleRotation(credential, tenantId, stateMachine);
                } catch (Exception e) {
                    log.warn("Rotation attempt {} failed for credential {}: {}",
                            attempt, credential.getKeyId(), e.getMessage());

                    stateMachine.incrementAttempt();

                    if (attempt < MAX_RETRIES) {
                        long backoffMs = RETRY_BACKOFF_SECONDS[attempt - 1] * 1000L;
                        log.info("Waiting {} ms before retry...", backoffMs);
                        Thread.sleep(backoffMs);
                    }
                }
            }

            result.setSuccess(false);
            result.setEndTime(Instant.now());
            result.setErrorMessage("All " + MAX_RETRIES + " rotation attempts failed");

            stateMachine.transitionTo(RotationState.FAIL,
                    "All retries exhausted for credential " + credential.getKeyId(),
                    result.getErrorMessage());
            auditTrailService.logRotationTransition(stateMachine.getTransitionLog().get(
                    stateMachine.getTransitionLog().size() - 1));

            auditTrailService.logEscalation(tenantId, credential.getId(), severity,
                    result.getErrorMessage(), MAX_RETRIES);

            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.setSuccess(false);
            result.setEndTime(Instant.now());
            result.setErrorMessage("Rotation interrupted");

            stateMachine.timeoutTransition();
            auditTrailService.logTimeout(tenantId, credential.getId(), severity);

            return result;
        } catch (Exception e) {
            result.setSuccess(false);
            result.setEndTime(Instant.now());
            result.setErrorMessage("Unexpected error: " + e.getMessage());

            stateMachine.transitionTo(RotationState.FAIL,
                    "Unexpected error during rotation", e.getMessage());
            auditTrailService.logRotationTransition(stateMachine.getTransitionLog().get(
                    stateMachine.getTransitionLog().size() - 1));

            return result;
        }
    }

    private RotationResult executeSingleRotation(Credential credential, String tenantId,
                                                  RotationStateMachine stateMachine) {
        RotationResult result = new RotationResult();
        result.setAlertId(credential.getId());
        result.setStartTime(Instant.now());

        Map<String, String> adminCredentials = vaultService.getAdminCredentials(tenantId);
        if (adminCredentials.isEmpty()) {
            throw new RuntimeException("No admin credentials found for tenant " + tenantId);
        }

        updateAccessKeyToInactive(credential.getKeyId());
        result.setMessage("Access key set to INACTIVE: " + credential.getKeyId());

        waitForIamPropagation(credential.getKeyId());

        CreateAccessKeyResponse createResponse = createNewAccessKey(adminCredentials);
        String newKeyId = createResponse.accessKey().accessKeyId();
        String newSecretKey = createResponse.accessKey().secretAccessKey();

        vaultService.storeNewCredentials(
                tenantId, newKeyId, newSecretKey, newKeyId,
                credential.getProviderArn() != null ? credential.getProviderArn() : null
        );

        result.setSuccess(true);
        result.setEndTime(Instant.now());
        result.setNewKeyId(newKeyId);
        result.setMessage("Successfully rotated credential " + credential.getKeyId() +
                          " -> " + newKeyId);

        stateMachine.transitionTo(RotationState.SUCCESS,
                "Rotation completed successfully for credential " + newKeyId);
        auditTrailService.logRotationTransition(stateMachine.getTransitionLog().get(
                stateMachine.getTransitionLog().size() - 1));

        auditTrailService.logRotationCompleted(tenantId, credential.getId(), result);
        log.info("Rotation completed for tenant {}: {}", tenantId, result.getNewKeyId());

        return result;
    }

    private void updateAccessKeyToInactive(String keyId) {
        log.info("Setting access key {} to INACTIVE", keyId);
        UpdateAccessKeyRequest request = UpdateAccessKeyRequest.builder()
                .accessKeyId(keyId)
                .status(STATUS_INACTIVE)
                .build();

        try {
            iamClient.updateAccessKey(request);
            log.info("Access key {} updated successfully to INACTIVE", keyId);
        } catch (NoSuchEntityException e) {
            log.warn("Access key {} not found (may have already been invalidated)", keyId);
        }
    }

    private void waitForIamPropagation(String keyId) {
        log.info("Waiting {} seconds for IAM propagation after deactivating {}",
                IAM_PROPAGATION_WAIT_SECONDS, keyId);

        Instant waitStart = Instant.now();
        while (Duration.between(waitStart, Instant.now()).getSeconds() < IAM_PROPAGATION_WAIT_SECONDS) {
            try {
                Thread.sleep(2000L);
                if (verifyKeyInactive(keyId)) {
                    log.info("Access key {} confirmed inactive after {} seconds",
                            keyId, Duration.between(waitStart, Instant.now()).getSeconds());
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Propagation wait interrupted");
                break;
            }
        }

        log.info("IAM propagation wait complete for key {} ({} seconds elapsed)",
                keyId, Duration.between(waitStart, Instant.now()).getSeconds());
    }

    private boolean verifyKeyInactive(String keyId) {
        try {
            ListAccessKeysRequest request = ListAccessKeysRequest.builder().build();
            ListAccessKeysResponse response = iamClient.listAccessKeys(request);

            for (var keyMetadata : response.accessKeyMetadata()) {
                if (keyMetadata.accessKeyId().equals(keyId)) {
                    boolean isActive = STATUS_ACTIVE.equals(keyMetadata.status());
                    log.debug("Access key {} status: {}", keyId, keyMetadata.status());
                    return !isActive;
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("Could not verify key status for {}: {}", keyId, e.getMessage());
            return false;
        }
    }

    private CreateAccessKeyResponse createNewAccessKey(Map<String, String> adminCredentials) {
        String userName = adminCredentials.getOrDefault("arn", "default");

        CreateAccessKeyRequest request = CreateAccessKeyRequest.builder()
                .userName(userName)
                .build();

        CreateAccessKeyResponse response = iamClient.createAccessKey(request);
        log.info("New access key created: {}", response.accessKey().accessKeyId());
        return response;
    }
}
