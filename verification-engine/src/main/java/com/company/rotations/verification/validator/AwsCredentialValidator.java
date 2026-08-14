package com.company.rotations.verification.validator;

import com.company.rotations.verification.enumeration.PermissionEnumerator;
import com.company.rotations.verification.model.PermissionMatrix;
import com.company.rotations.verification.model.VerificationResult;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;
import software.amazon.awssdk.services.sts.model.StsException;

import java.util.Collections;

@Service
public class AwsCredentialValidator {

    private static final Logger logger = LoggerFactory.getLogger(AwsCredentialValidator.class);

    private final PermissionEnumerator permissionEnumerator;
    private final RetryRegistry retryRegistry;
    private final Region defaultRegion;

    @Value("${app.verification.aws.region:us-east-1}")
    private String regionStr;

    public AwsCredentialValidator(PermissionEnumerator permissionEnumerator,
                                   RetryRegistry retryRegistry,
                                   @Value("${app.verification.aws.region:us-east-1}") String region) {
        this.permissionEnumerator = permissionEnumerator;
        this.retryRegistry = retryRegistry;
        this.defaultRegion = Region.of(region);
    }

    public VerificationResult validate(String accountId, String credentialId, String credentialValue) {
        logger.info("Validating AWS credential {} for account {}", credentialId, accountId);

        if (credentialValue == null || credentialValue.isBlank()) {
            logger.warn("Empty credential value for credential {}", credentialId);
            return VerificationResult.failed(accountId, credentialId, "Empty credential value");
        }

        AwsCredentialsProvider credentialsProvider = parseAwsCredentials(credentialId, credentialValue);
        if (credentialsProvider == null) {
            logger.warn("Could not parse AWS credentials for credential {}", credentialId);
            return VerificationResult.failed(accountId, credentialId, "Invalid AWS credential format");
        }

        try (StsClient stsClient = StsClient.builder()
                .region(defaultRegion)
                .credentialsProvider(credentialsProvider)
                .build()) {

            Retry awsRetry = retryRegistry.retry("awsApiRetry");

            GetCallerIdentityResponse identityResponse = awsRetry.executeSupplier(() ->
                    stsClient.getCallerIdentity(GetCallerIdentityRequest.builder().build()));

            String identityArn = identityResponse.arn();
            String retrievedAccountId = identityResponse.account();

            logger.info("STS GetCallerIdentity succeeded for credential {}: ARN={}, accountId={}",
                    credentialId, identityArn, retrievedAccountId);

            try (IamClient iamClient = IamClient.builder()
                    .region(defaultRegion)
                    .credentialsProvider(credentialsProvider)
                    .build()) {

                PermissionMatrix matrix = permissionEnumerator.enumeratePermissions(iamClient, identityArn);
                String lastUsedDate = "unknown";

                return VerificationResult.success(
                        retrievedAccountId != null ? retrievedAccountId : accountId,
                        identityArn,
                        matrix != null ? matrix.getEffectiveActions() : Collections.emptySet(),
                        lastUsedDate
                );

            } catch (Exception e) {
                logger.error("Error enumerating permissions for {}: {}", identityArn, e.getMessage());
                return VerificationResult.success(
                        retrievedAccountId != null ? retrievedAccountId : accountId,
                        identityArn,
                        Collections.emptySet(),
                        "unknown"
                );
            }

        } catch (StsException e) {
            return handleStsException(credentialId, accountId, e);
        } catch (Exception e) {
            logger.error("Unexpected error validating AWS credential {}: {}", credentialId, e.getMessage());
            return VerificationResult.failed(accountId, credentialId, "Unexpected error: " + e.getMessage());
        }
    }

    private AwsCredentialsProvider parseAwsCredentials(String credentialId, String credentialValue) {
        String[] parts = credentialValue.split(":");
        if (parts.length == 2) {
            String accessKeyId = parts[0];
            String secretAccessKey = parts[1];
            AwsBasicCredentials basicCreds = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
            return () -> basicCreds;
        }

        logger.warn("Credential {} does not match expected accessKey:secretKey format", credentialId);
        return null;
    }

    private VerificationResult handleStsException(String credentialId, String accountId, StsException e) {
        String errorCode = e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : "unknown";
        String errorMessage = e.getMessage();

        logger.warn("STS error for credential {}: {} - {}", credentialId, errorCode, errorMessage);

        if ("ExpiredTokenException".equals(errorCode)) {
            logger.info("Credential {} detected as INVALID (ExpiredTokenException)", credentialId);
            return VerificationResult.failed(accountId, credentialId, "Expired credentials");
        } else if ("InvalidClientTokenId".equals(errorCode)) {
            logger.info("Credential {} detected as INVALID (InvalidClientTokenId)", credentialId);
            return VerificationResult.failed(accountId, credentialId, "Invalid credentials");
        } else if ("AccessDenied".equals(errorCode)) {
            logger.info("Credential {} has AccessDenied - insufficient permissions", credentialId);
            return VerificationResult.failed(accountId, credentialId, "AccessDenied - insufficient permissions");
        } else {
            logger.error("Unhandled STS error for credential {}: {}", credentialId, e.getMessage());
            return VerificationResult.failed(accountId, credentialId, "STS error: " + errorCode);
        }
    }
}
