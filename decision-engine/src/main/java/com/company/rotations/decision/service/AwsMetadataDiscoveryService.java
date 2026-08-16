package com.company.rotations.decision.service;

import com.company.rotations.decision.domain.DiscoveredPermissions;
import com.company.rotations.models.ClientRule;
import com.company.rotations.models.Credential;
import com.company.rotations.decision.repository.ClientRuleRepository;
import com.company.rotations.decision.repository.CredentialRepository;
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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class AwsMetadataDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(AwsMetadataDiscoveryService.class);

    private static final String PERMISSION_HASH_PREFIX = "perms:";

    private final DroolsRuleService droolsRuleService;
    private final DroolsRuleGenerator droolsRuleGenerator;
    private final SemaphoreService semaphoreService;
    private final CredentialRepository credentialRepository;
    private final ClientRuleRepository clientRuleRepository;

    @Value("${decision.discovery.poll-interval-hours:3}")
    private int pollIntervalHours;

    @Value("${decision.discovery.semaphore-wait-seconds:30}")
    private int semaphoreWaitSeconds;

    @Value("${decision.discovery.enabled:true}")
    private boolean discoveryEnabled;

    public AwsMetadataDiscoveryService(DroolsRuleService droolsRuleService,
                                       DroolsRuleGenerator droolsRuleGenerator,
                                       SemaphoreService semaphoreService,
                                       CredentialRepository credentialRepository,
                                       ClientRuleRepository clientRuleRepository) {
        this.droolsRuleService = droolsRuleService;
        this.droolsRuleGenerator = droolsRuleGenerator;
        this.semaphoreService = semaphoreService;
        this.credentialRepository = credentialRepository;
        this.clientRuleRepository = clientRuleRepository;
    }

    @Scheduled(fixedRateString = "${decision.discovery.scheduled-rate:10800000}")
    public void scheduledDiscovery() {
        if (!discoveryEnabled) {
            log.debug("Scheduled discovery is disabled, skipping");
            return;
        }

        log.info("Scheduled AWS metadata discovery started (interval: {}h)", pollIntervalHours);

        List<Credential> activeCredentials = credentialRepository.findAll();

        List<Credential> activeOnly = new ArrayList<>();
        for (Credential cred : activeCredentials) {
            if (cred.getStatus() == Credential.CredentialStatus.ACTIVE && cred.getKeyId() != null) {
                activeOnly.add(cred);
            }
        }

        if (activeOnly.isEmpty()) {
            log.info("No active credentials found with key IDs, scheduled discovery complete");
            return;
        }

        Map<String, List<Credential>> tenantCredentials = groupByTenant(activeOnly);
        int processed = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;

        for (Map.Entry<String, List<Credential>> entry : tenantCredentials.entrySet()) {
            String tenantId = entry.getKey();
            List<Credential> credentials = entry.getValue();

            try {
                boolean changed = processTenantDiscovery(tenantId, credentials);
                if (changed) {
                    updated++;
                } else {
                    skipped++;
                }
                processed++;
            } catch (CredentialExpiredException e) {
                log.warn("Credentials expired for tenant {}, state: {}", tenantId, e.getState());
                processed++;
            } catch (Exception e) {
                log.error("Failed scheduled discovery for tenant {}: {}", tenantId, e.getMessage());
                failed++;
            }
        }

        log.info("Scheduled discovery complete: {} processed, {} updated, {} skipped (no changes), {} failed",
                processed, updated, skipped, failed);
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

    boolean processTenantDiscovery(String tenantId, List<Credential> credentials) {
        String accessKey = null;
        String secretKey = null;
        String region = "us-east-1";

        for (Credential cred : credentials) {
            if (cred.getKeyId() != null && cred.getKeyId().startsWith("AKIA")) {
                accessKey = cred.getKeyId();
                secretKey = cred.getKeySecret();
                break;
            }
        }

        if (accessKey == null && !credentials.isEmpty()) {
            accessKey = credentials.get(0).getKeyId();
            secretKey = credentials.get(0).getKeySecret();
        }

        Map<String, String> awsCredentials = new LinkedHashMap<>();
        awsCredentials.put("accessKey", accessKey);
        awsCredentials.put("secretKey", secretKey != null ? secretKey : "");
        awsCredentials.put("region", region);

        DiscoveredPermissions permissions;
        try {
            permissions = discover(tenantId, awsCredentials);
        } catch (CredentialExpiredException e) {
            throw e;
        } catch (Exception e) {
            log.error("Discovery failed for tenant {}: {}", tenantId, e.getMessage());
            return false;
        }

        String newDrl = droolsRuleGenerator.generate(tenantId, permissions);
        String newHash = hash(newDrl);

        Optional<ClientRule> activeRule = clientRuleRepository.findActiveByTenantId(tenantId);
        String currentHash = null;

        if (activeRule.isPresent()) {
            String currentDrl = new String(activeRule.get().getDrlContent(), java.nio.charset.StandardCharsets.UTF_8);
            currentHash = hash(currentDrl);
        }

        if (currentHash != null && newHash != null && currentHash.equals(newHash)) {
            log.debug("DRL content unchanged for tenant {}, skipping regeneration", tenantId);
            return false;
        }

        if (!semaphoreService.acquireSemaphore("rule_generation_" + tenantId, 15 * 60, 30)) {
            log.info("Semaphore held by another process, skipping discovery for tenant {}", tenantId);
            return false;
        }

        log.info("Permissions changed for tenant {}, regenerating DRL (hash: {} -> {})",
                tenantId, currentHash != null ? currentHash.substring(0, 8) : "null", newHash.substring(0, 8));

        boolean updated = droolsRuleService.updateRulesForTenant(tenantId, newDrl.getBytes(), "auto-generated");
        if (!updated) {
            log.error("Failed to update rules for tenant {} during scheduled discovery", tenantId);
            droolsRuleService.rollbackToLastValid(tenantId);
            return false;
        }

        return true;
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

    public PushDiscoveryResult pushDiscovery(String tenantId, Map<String, String> awsCredentials, String source) {
        log.info("Push discovery triggered for tenant {} from source: {}", tenantId, source);

        if (!semaphoreService.acquireSemaphore("rule_generation_" + tenantId, 15 * 60, 30)) {
            log.info("Semaphore held by another process, skipping push discovery for tenant {}", tenantId);
            return PushDiscoveryResult.skipped(tenantId);
        }

        DiscoveredPermissions permissions;
        try {
            permissions = discover(tenantId, awsCredentials);
        } catch (CredentialExpiredException e) {
            log.warn("Push discovery failed for tenant {}: credentials expired", tenantId);
            return PushDiscoveryResult.credentialExpired(tenantId, e.getState());
        } catch (Exception e) {
            log.error("Push discovery failed for tenant {}: {}", tenantId, e.getMessage());
            return PushDiscoveryResult.failed(tenantId, e.getMessage());
        }

        String newDrl = droolsRuleGenerator.generate(tenantId, permissions);
        String newHash = hash(newDrl);

        Optional<ClientRule> activeRule = clientRuleRepository.findActiveByTenantId(tenantId);
        String currentHash = null;

        if (activeRule.isPresent()) {
            String currentDrl = new String(activeRule.get().getDrlContent(), java.nio.charset.StandardCharsets.UTF_8);
            currentHash = hash(currentDrl);
        }

        if (currentHash != null && newHash != null && currentHash.equals(newHash)) {
            log.info("Push discovery: no permission changes for tenant {}, DRL unchanged", tenantId);
            return PushDiscoveryResult.noChanges(tenantId, newHash);
        }

        log.info("Push discovery: permissions changed for tenant {}, regenerating DRL", tenantId);

        int errors = droolsRuleService.validateDrl(newDrl.getBytes());
        if (errors > 0) {
            log.error("Push discovery: DRL validation failed for tenant {}, {} errors", tenantId, errors);
            return PushDiscoveryResult.validationFailed(tenantId, errors);
        }

        if (newDrl.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > droolsRuleService.getMaxDrlSizeBytes()) {
            log.error("Push discovery: DRL exceeds max size {} bytes for tenant {}",
                    droolsRuleService.getMaxDrlSizeBytes(), tenantId);
            return PushDiscoveryResult.validationFailed(tenantId, 1);
        }

        Integer currentVersion = clientRuleRepository.findMaxVersionByTenantId(tenantId);
        int newVersion = (currentVersion == null ? 0 : currentVersion) + 1;

        ClientRule newRule = new ClientRule();
        newRule.setTenantId(tenantId);
        newRule.setVersion(newVersion);
        newRule.setDrlContent(newDrl.getBytes());
        newRule.setPlaybookId("auto-generated");
        newRule.setActive(true);

        ClientRule saved = clientRuleRepository.save(newRule);
        droolsRuleService.invalidateCache(tenantId);

        log.info("Push discovery complete for tenant {}, new version: {}, hash: {}",
                tenantId, newVersion, newHash.substring(0, 8));

        return PushDiscoveryResult.updated(tenantId, newVersion, newHash, source);
    }

    private String hash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return PERMISSION_HASH_PREFIX + hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 not available", e);
            return PERMISSION_HASH_PREFIX + content.hashCode();
        }
    }

    private Map<String, List<Credential>> groupByTenant(List<Credential> credentials) {
        Map<String, List<Credential>> result = new LinkedHashMap<>();
        for (Credential cred : credentials) {
            result.computeIfAbsent(cred.getTenantId(), k -> new ArrayList<>()).add(cred);
        }
        return result;
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

    public static class PushDiscoveryResult {
        public enum Status { UPDATED, NO_CHANGES, SKIPPED, CREDENTIAL_EXPIRED, FAILED, VALIDATION_FAILED }

        private final Status status;
        private final String tenantId;
        private final Integer version;
        private final String hash;
        private final String errorMessage;
        private final String source;

        private PushDiscoveryResult(Status status, String tenantId, Integer version, String hash,
                                     String errorMessage, String source) {
            this.status = status;
            this.tenantId = tenantId;
            this.version = version;
            this.hash = hash;
            this.errorMessage = errorMessage;
            this.source = source;
        }

        public static PushDiscoveryResult updated(String tenantId, int version, String hash, String source) {
            return new PushDiscoveryResult(Status.UPDATED, tenantId, version, hash, null, source);
        }

        public static PushDiscoveryResult noChanges(String tenantId, String hash) {
            return new PushDiscoveryResult(Status.NO_CHANGES, tenantId, null, hash, null, null);
        }

        public static PushDiscoveryResult skipped(String tenantId) {
            return new PushDiscoveryResult(Status.SKIPPED, tenantId, null, null, null, null);
        }

        public static PushDiscoveryResult credentialExpired(String tenantId, String state) {
            return new PushDiscoveryResult(Status.CREDENTIAL_EXPIRED, tenantId, null, null,
                    "PENDING: CRED_REFRESH - " + state, null);
        }

        public static PushDiscoveryResult failed(String tenantId, String errorMessage) {
            return new PushDiscoveryResult(Status.FAILED, tenantId, null, null, errorMessage, null);
        }

        public static PushDiscoveryResult validationFailed(String tenantId, int errorCount) {
            return new PushDiscoveryResult(Status.VALIDATION_FAILED, tenantId, null, null,
                    "DRL validation failed with " + errorCount + " errors", null);
        }

        public Status getStatus() { return status; }
        public String getTenantId() { return tenantId; }
        public Integer getVersion() { return version; }
        public String getHash() { return hash; }
        public String getErrorMessage() { return errorMessage; }
        public String getSource() { return source; }
    }
}
