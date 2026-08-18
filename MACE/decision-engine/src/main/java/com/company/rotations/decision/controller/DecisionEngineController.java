package com.company.rotations.decision.controller;

import com.company.rotations.decision.domain.Playbook;
import com.company.rotations.decision.service.AwsMetadataDiscoveryService;
import com.company.rotations.decision.service.CriticalityCalculator;
import com.company.rotations.decision.service.DroolsRuleService;
import com.company.rotations.decision.service.PlaybookLoaderService;
import com.company.rotations.models.ClientRule;
import com.company.rotations.decision.repository.ClientRuleRepository;
import com.company.rotations.models.Severidad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class DecisionEngineController {

    private static final Logger log = LoggerFactory.getLogger(DecisionEngineController.class);

    private final CriticalityCalculator criticalityCalculator;
    private final PlaybookLoaderService playbookLoaderService;
    private final DroolsRuleService droolsRuleService;
    private final ClientRuleRepository clientRuleRepository;
    private final AwsMetadataDiscoveryService discoveryService;

    public DecisionEngineController(CriticalityCalculator criticalityCalculator,
                                     PlaybookLoaderService playbookLoaderService,
                                     DroolsRuleService droolsRuleService,
                                     ClientRuleRepository clientRuleRepository,
                                     AwsMetadataDiscoveryService discoveryService) {
        this.criticalityCalculator = criticalityCalculator;
        this.playbookLoaderService = playbookLoaderService;
        this.droolsRuleService = droolsRuleService;
        this.clientRuleRepository = clientRuleRepository;
        this.discoveryService = discoveryService;
    }

    @GetMapping("/{tenantId}/rules")
    public ResponseEntity<Map<String, Object>> getRules(@PathVariable String tenantId) {
        Map<String, Object> response = new LinkedHashMap<>();
        Optional<ClientRule> activeRule = clientRuleRepository.findActiveByTenantId(tenantId);

        if (activeRule.isPresent()) {
            ClientRule rule = activeRule.get();
            response.put("tenantId", tenantId);
            response.put("version", rule.getVersion());
            response.put("drlContent", new String(rule.getDrlContent(), java.nio.charset.StandardCharsets.UTF_8));
            response.put("drlSizeBytes", rule.getDrlSizeBytes());
            response.put("active", rule.isActive());
            response.put("manualOverride", rule.isManualOverrideByClient());
            response.put("createdAt", rule.getCreatedAt());
        } else {
            response.put("tenantId", tenantId);
            response.put("rules", Collections.emptyList());
            response.put("message", "No rules found for tenant");
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{tenantId}/rules")
    public ResponseEntity<Map<String, Object>> updateRules(@PathVariable String tenantId,
                                                              @RequestBody Map<String, Object> request) {
        Object severityOverride = request.get("severity");
        boolean manualOverride = request.containsKey("manualOverride") &&
                                 Boolean.TRUE.equals(request.get("manualOverride"));
        String user = request.containsKey("user") ? String.valueOf(request.get("user")) : "system";

        Map<String, Object> response = new LinkedHashMap<>();

        if (severityOverride != null) {
            try {
                Severidad clientSeverity = Severidad.fromString(String.valueOf(severityOverride));
                Optional<ClientRule> activeRule = clientRuleRepository.findActiveByTenantId(tenantId);
                Playbook playbook = playbookLoaderService.loadPlaybookById(
                        activeRule.map(ClientRule::getPlaybookId).orElse("aws-access-key-exposed")
                );

                if (playbook != null && !playbook.getCanLowerFloor()) {
                    Map<String, Severidad> floor = playbook.getSeverityFloor();
                    if (!floor.isEmpty()) {
                        Severidad lowestFloor = floor.values().stream()
                                .min(Comparator.comparingInt(Severidad::getRank))
                                .orElse(Severidad.BAJO);

                        if (clientSeverity.getRank() < lowestFloor.getRank()) {
                            response.put("error", "Cannot lower below playbook floor: " + lowestFloor);
                            response.put("playbookFloor", lowestFloor);
                            return ResponseEntity.badRequest().body(response);
                        }
                    }
                }

                response.put("applied", true);
                response.put("severity", clientSeverity);
                response.put("manualOverride", true);
                response.put("user", user);

            } catch (IllegalArgumentException e) {
                response.put("error", "Invalid severity: " + severityOverride);
                return ResponseEntity.badRequest().body(response);
            }
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{tenantId}/rules/discover")
    public ResponseEntity<Map<String, Object>> discoverAndRegenerate(@PathVariable String tenantId,
                                                                        @RequestBody Map<String, String> credentials) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            discoveryService.discoverAndRegenerate(tenantId, credentials);
            response.put("status", "success");
            response.put("tenantId", tenantId);
        } catch (AwsMetadataDiscoveryService.CredentialExpiredException e) {
            response.put("status", "credential_expired");
            response.put("state", e.getState());
            response.put("message", "AWS credentials have expired. Please update credentials.");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/playbooks")
    public ResponseEntity<List<Map<String, Object>>> getAllPlaybooks() {
        List<Map<String, Object>> playbooks = new ArrayList<>();
        String[] playbookIds = {"aws-access-key-exposed", "aws-session-token-leaked",
                                 "aws-root-credentials-exposed", "aws-iam-role-assumption-abuse"};

        for (String id : playbookIds) {
            Playbook playbook = playbookLoaderService.loadPlaybookById(id);
            if (playbook != null) {
                Map<String, Object> pb = new LinkedHashMap<>();
                pb.put("playbookId", playbook.getPlaybookId());
                pb.put("version", playbook.getVersion());
                pb.put("credentialTypes", playbook.getCredentialTypes());
                pb.put("severityFloor", playbook.getSeverityFloor());
                pb.put("autoRotate", playbook.getAutoRotate());
                pb.put("canLowerFloor", playbook.getCanLowerFloor());
                playbooks.add(pb);
            }
        }

        return ResponseEntity.ok(playbooks);
    }

    @PostMapping("/validate-drl")
    public ResponseEntity<Map<String, Object>> validateDrl(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new LinkedHashMap<>();
        String drlContent = request.get("drl");
        if (drlContent == null || drlContent.isEmpty()) {
            response.put("valid", false);
            response.put("error", "DRL content is required");
            return ResponseEntity.badRequest().body(response);
        }

        int errors = droolsRuleService.validateDrl(drlContent.getBytes());
        response.put("valid", errors <= 0);
        response.put("errors", errors);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/webhooks/credential-exposure")
    public ResponseEntity<Map<String, Object>> handleCredentialExposureWebhook(
            @RequestHeader(value = "X-Webhook-Source", required = false, defaultValue = "unknown") String webhookSource,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestBody Map<String, Object> payload) {

        log.info("Received credential exposure webhook from source: {}, has signature: {}",
                webhookSource, signature != null);

        Map<String, Object> response = new LinkedHashMap<>();

        String tenantId = extractTenantId(payload);
        if (tenantId == null || tenantId.isBlank()) {
            response.put("status", "error");
            response.put("message", "tenantId is required in webhook payload");
            return ResponseEntity.badRequest().body(response);
        }

        String affectedResource = extractResource(payload);
        log.info("Credential exposure event: tenant={}, resource={}, source={}",
                tenantId, affectedResource, webhookSource);

        Map<String, String> awsCredentials = new LinkedHashMap<>();
        if (payload.containsKey("awsCredentials")) {
            Object credsObj = payload.get("awsCredentials");
            if (credsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> credsMap = (Map<String, Object>) credsObj;
                for (Map.Entry<String, Object> entry : credsMap.entrySet()) {
                    awsCredentials.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
        }

        if (awsCredentials.isEmpty()) {
            response.put("status", "queued");
            response.put("tenantId", tenantId);
            response.put("message", "Webhook received. Discovery queued but credentials not provided - " +
                    "use the /discover endpoint with credentials or configure credentials in the secrets store.");
            response.put("affectedResource", affectedResource);
            response.put("source", webhookSource);
            return ResponseEntity.accepted().body(response);
        }

        AwsMetadataDiscoveryService.PushDiscoveryResult result;
        try {
            result = discoveryService.pushDiscovery(tenantId, awsCredentials, webhookSource);
        } catch (Exception e) {
            log.error("Push discovery failed for tenant {}: {}", tenantId, e.getMessage());
            response.put("status", "error");
            response.put("message", "Push discovery failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        response.put("status", result.getStatus().name().toLowerCase());
        response.put("tenantId", result.getTenantId());
        response.put("source", webhookSource);
        response.put("affectedResource", affectedResource);

        if (result.getVersion() != null) {
            response.put("newVersion", result.getVersion());
        }
        if (result.getHash() != null) {
            response.put("drlHash", result.getHash().substring(0, Math.min(16, result.getHash().length())));
        }
        if (result.getErrorMessage() != null) {
            response.put("message", result.getErrorMessage());
        } else {
            response.put("message", getMessageForStatus(result.getStatus()));
        }

        switch (result.getStatus()) {
            case UPDATED:
                return ResponseEntity.ok(response);
            case NO_CHANGES:
            case SKIPPED:
                return ResponseEntity.ok(response);
            case CREDENTIAL_EXPIRED:
                return ResponseEntity.status(401).body(response);
            case VALIDATION_FAILED:
                return ResponseEntity.status(422).body(response);
            case FAILED:
                return ResponseEntity.status(500).body(response);
            default:
                return ResponseEntity.ok(response);
        }
    }

    @PostMapping("/webhooks/discovery/pull")
    public ResponseEntity<Map<String, Object>> triggerPullDiscovery(
            @RequestHeader(value = "X-Webhook-Source", required = false, defaultValue = "internal") String source,
            @RequestBody Map<String, Object> payload) {

        Map<String, Object> response = new LinkedHashMap<>();

        String tenantId = extractTenantId(payload);
        if (tenantId == null || tenantId.isBlank()) {
            response.put("status", "error");
            response.put("message", "tenantId is required");
            return ResponseEntity.badRequest().body(response);
        }

        Map<String, String> awsCredentials = new LinkedHashMap<>();
        if (payload.containsKey("awsCredentials")) {
            Object credsObj = payload.get("awsCredentials");
            if (credsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> credsMap = (Map<String, Object>) credsObj;
                for (Map.Entry<String, Object> entry : credsMap.entrySet()) {
                    awsCredentials.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
        }

        if (awsCredentials.isEmpty()) {
            response.put("status", "error");
            response.put("message", "awsCredentials are required for pull discovery");
            return ResponseEntity.badRequest().body(response);
        }

        AwsMetadataDiscoveryService.PushDiscoveryResult result;
        try {
            result = discoveryService.pushDiscovery(tenantId, awsCredentials, "pull:" + source);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Pull discovery failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        response.put("status", result.getStatus().name().toLowerCase());
        response.put("tenantId", result.getTenantId());
        response.put("source", "pull:" + source);
        if (result.getVersion() != null) response.put("newVersion", result.getVersion());
        if (result.getErrorMessage() != null) response.put("message", result.getErrorMessage());
        else response.put("message", getMessageForStatus(result.getStatus()));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/webhooks/discovery/batch")
    public ResponseEntity<Map<String, Object>> triggerBatchDiscovery(
            @RequestHeader(value = "X-Webhook-Source", required = false, defaultValue = "internal") String source,
            @RequestBody Map<String, Object> payload) {

        Map<String, Object> response = new LinkedHashMap<>();

        @SuppressWarnings("unchecked")
        List<String> tenantIds = payload.containsKey("tenantIds")
                ? (List<String>) payload.get("tenantIds")
                : Collections.emptyList();

        if (tenantIds.isEmpty()) {
            response.put("status", "error");
            response.put("message", "tenantIds array is required");
            return ResponseEntity.badRequest().body(response);
        }

        Map<String, String> awsCredentials = new LinkedHashMap<>();
        if (payload.containsKey("awsCredentials")) {
            Object credsObj = payload.get("awsCredentials");
            if (credsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> credsMap = (Map<String, Object>) credsObj;
                for (Map.Entry<String, Object> entry : credsMap.entrySet()) {
                    awsCredentials.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
        }

        int processed = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        List<Map<String, Object>> results = new ArrayList<>();

        for (String tenantId : tenantIds) {
            AwsMetadataDiscoveryService.PushDiscoveryResult result;
            try {
                result = discoveryService.pushDiscovery(tenantId, awsCredentials, "batch:" + source);
            } catch (Exception e) {
                log.error("Batch discovery failed for tenant {}: {}", tenantId, e.getMessage());
                failed++;
                results.add(Map.of("tenantId", tenantId, "status", "error", "message", e.getMessage()));
                continue;
            }

            processed++;
            Map<String, Object> itemResult = new LinkedHashMap<>();
            itemResult.put("tenantId", tenantId);
            itemResult.put("status", result.getStatus().name().toLowerCase());

            if (result.getVersion() != null) itemResult.put("newVersion", result.getVersion());
            if (result.getErrorMessage() != null) itemResult.put("message", result.getErrorMessage());

            switch (result.getStatus()) {
                case UPDATED: updated++; break;
                case NO_CHANGES:
                case SKIPPED: skipped++; break;
                case FAILED: failed++; break;
                default: skipped++; break;
            }

            results.add(itemResult);
        }

        response.put("status", "completed");
        response.put("source", "batch:" + source);
        response.put("processed", processed);
        response.put("updated", updated);
        response.put("skipped", skipped);
        response.put("failed", failed);
        response.put("results", results);

        return ResponseEntity.ok(response);
    }

    private String extractTenantId(Map<String, Object> payload) {
        if (payload == null) return null;
        Object tenantId = payload.get("tenantId");
        if (tenantId == null) {
            tenantId = payload.get("tenant_id");
        }
        if (tenantId == null && payload.containsKey("tenant")) {
            tenantId = payload.get("tenant");
        }
        return tenantId != null ? String.valueOf(tenantId) : null;
    }

    private String extractResource(Map<String, Object> payload) {
        if (payload == null) return null;
        Object resource = payload.get("resource");
        if (resource == null) {
            resource = payload.get("affectedResource");
        }
        if (resource == null) {
            resource = payload.get("affected_resource");
        }
        if (resource == null && payload.containsKey("accessKeyId")) {
            resource = payload.get("accessKeyId");
        }
        return resource != null ? String.valueOf(resource) : null;
    }

    private String getMessageForStatus(AwsMetadataDiscoveryService.PushDiscoveryResult.Status status) {
        return switch (status) {
            case UPDATED -> "Rules updated successfully";
            case NO_CHANGES -> "No permission changes detected";
            case SKIPPED -> "Discovery skipped (semaphore held by another process)";
            case CREDENTIAL_EXPIRED -> "Credentials expired, pending refresh";
            case VALIDATION_FAILED -> "Generated DRL failed validation";
            case FAILED -> "Discovery failed";
        };
    }
}
