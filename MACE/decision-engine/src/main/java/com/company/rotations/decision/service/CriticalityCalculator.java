package com.company.rotations.decision.service;

import com.company.rotations.logging.service.AuditService;
import com.company.rotations.decision.domain.CriticalityResult;
import com.company.rotations.decision.domain.Playbook;
import com.company.rotations.models.Severidad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CriticalityCalculator {

    private static final Logger log = LoggerFactory.getLogger(CriticalityCalculator.class);

    private final PlaybookLoaderService playbookLoaderService;
    private final AuditService auditService;

    public CriticalityCalculator(PlaybookLoaderService playbookLoaderService,
                                  AuditService auditService) {
        this.playbookLoaderService = playbookLoaderService;
        this.auditService = auditService;
    }

    public CriticalityResult calculateCriticality(String tenantId, String credentialType,
                                                    Map<String, Object> actionMatrix,
                                                    Map<String, Object> metadata) {
        Playbook playbook = playbookLoaderService.loadPlaybook(credentialType);
        if (playbook == null) {
            log.warn("No playbook found for credential type {}", credentialType);
            return new CriticalityResult(
                    Severidad.BAJO, Severidad.BAJO, Severidad.BAJO,
                    "No playbook matched credential type " + credentialType,
                    "none", "no_playbook_match"
            );
        }

        Severidad playbookFloor = determinePlaybookFloor(playbook, actionMatrix, metadata);
        Severidad clientRules = evaluateClientRules(tenantId, credentialType);

        Severidad finalCriticality = SeveridadUtil.max(playbookFloor, clientRules);

        String rationale = String.format(
                "max(playbook_floor=%s, client_rules=%s) = %s",
                playbookFloor, clientRules, finalCriticality
        );

        log.info("Criticality calculated: {} for tenant {} (playbookFloor={}, clientRules={})",
                finalCriticality, tenantId, playbookFloor, clientRules);

        try {
            auditService.logRuleEvaluated(Map.of(
                    "tenant_id", tenantId,
                    "credential_type", credentialType,
                    "playbook_id", playbook.getPlaybookId(),
                    "playbook_floor", playbookFloor.name(),
                    "client_rules", clientRules.name(),
                    "calculated_severity", finalCriticality.name(),
                    "calculated_via", "max(playbook_floor, client_rules)",
                    "rationale", rationale
            ));
        } catch (Exception e) {
            log.warn("Could not log criticality calculation audit: {}", e.getMessage());
        }

        return new CriticalityResult(
                finalCriticality,
                playbookFloor,
                clientRules,
                rationale,
                playbook.getPlaybookId(),
                "max(playbook_floor, client_rules)"
        );
    }

    private Severidad determinePlaybookFloor(Playbook playbook, Map<String, Object> actionMatrix,
                                              Map<String, Object> metadata) {
        Map<String, Severidad> severityFloor = playbook.getSeverityFloor();
        if (severityFloor == null || severityFloor.isEmpty()) {
            return Severidad.BAJO;
        }

        String highestPermission = getHighestPermission(actionMatrix, metadata);
        if (highestPermission != null) {
            Severidad floor = severityFloor.get(highestPermission.toLowerCase().replace(" ", "_"));
            if (floor != null) {
                return floor;
            }
        }

        return Severidad.BAJO;
    }

    private Severidad evaluateClientRules(String tenantId, String credentialType) {
        return Severidad.BAJO;
    }

    private String getHighestPermission(Map<String, Object> actionMatrix, Map<String, Object> metadata) {
        if (actionMatrix == null || actionMatrix.isEmpty()) {
            return null;
        }

        String highest = null;
        int highestRank = -1;

        for (Map.Entry<String, Object> entry : actionMatrix.entrySet()) {
            String action = entry.getKey();
            Object value = entry.getValue();
            boolean hasPermission = value instanceof Boolean && (Boolean) value;

            if (hasPermission) {
                Severidad floor = severityForAction(action);
                if (floor.getRank() > highestRank) {
                    highestRank = floor.getRank();
                    highest = action;
                }
            }
        }

        return highest;
    }

    private Severidad severityForAction(String action) {
        String lower = action.toLowerCase();
        if (lower.contains("s3") && (lower.contains("write") || lower.contains("delete") || lower.contains("full"))) {
            return Severidad.CRITICO;
        }
        if (lower.contains("iam") && (lower.contains("modify") || lower.contains("admin") || lower.contains("attach"))) {
            return Severidad.CRITICO;
        }
        if (lower.contains("ec2") && (lower.contains("control") || lower.contains("manage") || lower.contains("modify"))) {
            return Severidad.CRITICO;
        }
        if (lower.contains("s3") && lower.contains("read")) {
            return Severidad.ALTO;
        }
        if (lower.contains("ec2") && lower.contains("read")) {
            return Severidad.MEDIA;
        }
        return Severidad.BAJO;
    }
}
