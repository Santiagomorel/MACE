package com.company.rotations.decision.service;

import com.company.rotations.logging.service.AuditService;
import com.company.rotations.decision.domain.CriticalityResult;
import com.company.rotations.decision.domain.Playbook;
import com.company.rotations.decision.repository.ClientRuleRepository;
import com.company.rotations.models.ClientRule;
import com.company.rotations.models.Severidad;
import com.company.rotations.spi.DecisionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DecisionEngineService implements DecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(DecisionEngineService.class);

    private final CriticalityCalculator criticalityCalculator;
    private final DroolsRuleService droolsRuleService;
    private final PlaybookLoaderService playbookLoaderService;
    private final ClientRuleRepository clientRuleRepository;
    private final AuditService auditService;

    public DecisionEngineService(CriticalityCalculator criticalityCalculator,
                                   DroolsRuleService droolsRuleService,
                                   PlaybookLoaderService playbookLoaderService,
                                   ClientRuleRepository clientRuleRepository,
                                   AuditService auditService) {
        this.criticalityCalculator = criticalityCalculator;
        this.droolsRuleService = droolsRuleService;
        this.playbookLoaderService = playbookLoaderService;
        this.clientRuleRepository = clientRuleRepository;
        this.auditService = auditService;
    }

    @Override
    public DecisionResult evaluate(UUID alertId, String tenantId, String credentialType,
                                    Map<String, Object> actionMatrix,
                                    Map<String, Object> metadata) {
        log.info("Evaluating decision for alert {} tenant {} credentialType {}", alertId, tenantId, credentialType);

        Map<String, Object> ruleEvalData = new LinkedHashMap<>();
        ruleEvalData.put("alert_id", alertId.toString());
        ruleEvalData.put("tenant_id", tenantId);
        ruleEvalData.put("credential_type", credentialType);
        ruleEvalData.put("action_matrix_size", actionMatrix != null ? actionMatrix.size() : 0);

        try {
            auditService.logRuleEvaluated(ruleEvalData);
        } catch (Exception e) {
            log.warn("Could not log rule evaluation audit: {}", e.getMessage());
        }

        CriticalityResult result = criticalityCalculator.calculateCriticality(
                tenantId, credentialType, actionMatrix, metadata
        );

        ClientRule activeRule = clientRuleRepository.findActiveByTenantId(tenantId).orElse(null);
        Integer evaluatedVersion = activeRule != null ? activeRule.getVersion() : null;

        Map<String, Object> decisionEventData = new LinkedHashMap<>();
        decisionEventData.put("alert_id", alertId.toString());
        decisionEventData.put("tenant_id", tenantId);
        decisionEventData.put("calculated_severity", result.getCalculatedCriticality().name());
        decisionEventData.put("playbook_floor", result.getPlaybookFloor().name());
        decisionEventData.put("client_rules", result.getClientRules().name());
        decisionEventData.put("playbook_id", result.getPlaybookId());
        decisionEventData.put("calculated_via", result.getCalculatedVia());
        decisionEventData.put("evaluated_rule_version", evaluatedVersion);
        decisionEventData.put("rationale", result.getRationale());

        try {
            auditService.logRuleEvaluated(Map.of(
                    "alert_id", alertId.toString(),
                    "tenant_id", tenantId,
                    "credential_type", credentialType,
                    "calculated_severity", result.getCalculatedCriticality().name(),
                    "playbook_floor", result.getPlaybookFloor().name(),
                    "client_rules", result.getClientRules().name(),
                    "playbook_id", result.getPlaybookId(),
                    "calculated_via", result.getCalculatedVia(),
                    "evaluated_rule_version", evaluatedVersion,
                    "rationale", result.getRationale()
            ));
        } catch (Exception e) {
            log.warn("Could not log decision audit event: {}", e.getMessage());
        }

        return new DecisionResultImpl(
                result.getCalculatedCriticality(),
                result.getRationale(),
                result.getPlaybookId(),
                result.getCalculatedVia(),
                extractComplianceTags(result.getPlaybookId()),
                evaluatedVersion
        );
    }

    private Map<String, Object> extractComplianceTags(String playbookId) {
        Playbook playbook = playbookLoaderService.loadPlaybookById(playbookId);
        if (playbook == null || playbook.getComplianceTags() == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> tags = new LinkedHashMap<>();
        for (Playbook.ComplianceTag tag : playbook.getComplianceTags()) {
            tags.put(tag.getSource(), tag.getControlDescription());
        }
        return tags;
    }

    private static class DecisionResultImpl implements DecisionResult {
        private final Severidad severity;
        private final String rationale;
        private final String playbookId;
        private final String calculatedVia;
        private final Map<String, Object> complianceTags;
        private final Integer evaluatedRuleVersion;

        public DecisionResultImpl(Severidad severity, String rationale, String playbookId,
                                   String calculatedVia, Map<String, Object> complianceTags,
                                   Integer evaluatedRuleVersion) {
            this.severity = severity;
            this.rationale = rationale;
            this.playbookId = playbookId;
            this.calculatedVia = calculatedVia;
            this.complianceTags = complianceTags;
            this.evaluatedRuleVersion = evaluatedRuleVersion;
        }

        @Override public Severidad getSeverity() { return severity; }
        @Override public String getRationale() { return rationale; }
        @Override public String getPlaybookId() { return playbookId; }
        @Override public String getCalculatedVia() { return calculatedVia; }
        @Override public Map<String, Object> getComplianceTags() { return complianceTags; }
        @Override public Integer getEvaluatedRuleVersion() { return evaluatedRuleVersion; }
    }
}
