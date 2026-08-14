package com.company.rotations.decision.controller;

import com.company.rotations.decision.domain.Playbook;
import com.company.rotations.decision.service.AwsMetadataDiscoveryService;
import com.company.rotations.decision.service.CriticalityCalculator;
import com.company.rotations.decision.service.DroolsRuleService;
import com.company.rotations.decision.service.PlaybookLoaderService;
import com.company.rotations.models.ClientRule;
import com.company.rotations.decision.repository.ClientRuleRepository;
import com.company.rotations.models.Severidad;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class DecisionEngineController {

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
}
