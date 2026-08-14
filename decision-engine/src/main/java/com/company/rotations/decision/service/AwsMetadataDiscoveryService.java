package com.company.rotations.decision.service;

import com.company.rotations.decision.domain.DiscoveredPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.AccessKeyMetadata;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.model.IamException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import java.util.*;

@Service
public class AwsMetadataDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(AwsMetadataDiscoveryService.class);

    private final DroolsRuleService droolsRuleService;
    private final DroolsRuleGenerator droolsRuleGenerator;
    private final SemaphoreService semaphoreService;

    @Value("${decision.discovery.poll-interval-hours:3}")
    private int pollIntervalHours;

    @Value("${decision.discovery.semaphore-wait-seconds:30}")
    private int semaphoreWaitSeconds;

    public AwsMetadataDiscoveryService(DroolsRuleService droolsRuleService,
                                       DroolsRuleGenerator droolsRuleGenerator,
                                       SemaphoreService semaphoreService) {
        this.droolsRuleService = droolsRuleService;
        this.droolsRuleGenerator = droolsRuleGenerator;
        this.semaphoreService = semaphoreService;
    }

    @Scheduled(fixedRateString = "${decision.discovery.scheduled-rate:10800000}")
    public void scheduledDiscovery() {
        log.info("Scheduled AWS metadata discovery started");
        // In production, iterate over tenants and discover
    }

    public DiscoveredPermissions discover(String tenantId, Map<String, String> awsCredentials) {
        log.info("Starting AWS metadata discovery for tenant {}", tenantId);

        String accessKey = awsCredentials.get("accessKey");
        String secretKey = awsCredentials.get("secretKey");
        String region = awsCredentials.getOrDefault("region", "us-east-1");

        AwsCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey));

        try (StsClient stsClient = StsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
             IamClient iamClient = IamClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(credentialsProvider)
                    .build()) {

            boolean s3FullAccess = checkS3FullAccess(iamClient, accessKey, region);
            boolean s3ReadOnly = checkS3ReadOnly(iamClient, accessKey, region);
            boolean iamModify = checkIamModify(iamClient, accessKey);
            boolean ec2InstanceControl = checkEc2InstanceControl(iamClient, accessKey, region);
            boolean ec2ReadOnly = checkEc2ReadOnly(iamClient, accessKey, region);
            boolean cloudWatchRead = checkCloudWatchRead(iamClient, accessKey, region);
            boolean nothingActive = !s3FullAccess && !s3ReadOnly && !iamModify &&
                                    !ec2InstanceControl && !ec2ReadOnly && !cloudWatchRead;

            return new DiscoveredPermissions(
                    tenantId, s3FullAccess, s3ReadOnly, iamModify,
                    ec2InstanceControl, ec2ReadOnly, cloudWatchRead, nothingActive
            );

        } catch (IamException e) {
            if (e.awsErrorDetails().errorCode().equals("AccessDenied") ||
                e.awsErrorDetails().errorCode().equals("AWSSTSExpired")) {
                log.warn("Credentials expired or access denied for tenant {}: {}", tenantId, e.getMessage());
                throw new CredentialExpiredException("PENDING: CRED_REFRESH", e);
            }
            throw e;
        }
    }

    public void discoverAndRegenerate(String tenantId, Map<String, String> awsCredentials) {
        DiscoveredPermissions permissions = discover(tenantId, awsCredentials);
        String drl = droolsRuleGenerator.generate(tenantId, permissions);

        if (!semaphoreService.acquireSemaphore("rule_generation_" + tenantId, 15 * 60, 30)) {
            log.info("Semaphore held by another process, skipping discovery for tenant {}", tenantId);
            return;
        }

        boolean updated = droolsRuleService.updateRulesForTenant(tenantId, drl.getBytes(), "auto-generated");
        if (!updated) {
            log.error("Failed to update rules for tenant {}", tenantId);
            droolsRuleService.rollbackToLastValid(tenantId);
        }
    }

    private boolean checkS3FullAccess(IamClient iamClient, String accessKey, String region) {
        try {
            return false; // TODO: implement with real AWS API calls
        } catch (Exception e) {
            log.warn("Failed to check S3 access: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkS3ReadOnly(IamClient iamClient, String accessKey, String region) {
        try {
            return false;
        } catch (Exception e) {
            log.warn("Failed to check S3 read access: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkIamModify(IamClient iamClient, String accessKey) {
        try {
            return false;
        } catch (Exception e) {
            log.warn("Failed to check IAM modify: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkEc2InstanceControl(IamClient iamClient, String accessKey, String region) {
        try {
            return false;
        } catch (Exception e) {
            log.warn("Failed to check EC2 control: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkEc2ReadOnly(IamClient iamClient, String accessKey, String region) {
        try {
            return false;
        } catch (Exception e) {
            log.warn("Failed to check EC2 read: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkCloudWatchRead(IamClient iamClient, String accessKey, String region) {
        try {
            return false;
        } catch (Exception e) {
            log.warn("Failed to check CloudWatch read: {}", e.getMessage());
            return false;
        }
    }

    public static class CredentialExpiredException extends RuntimeException {
        private final String state;

        public CredentialExpiredException(String state, Throwable cause) {
            super(cause);
            this.state = state;
        }

        public String getState() { return state; }
    }
}
