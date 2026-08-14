package com.company.rotations.decision.service;

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

    public DecisionEngineService(CriticalityCalculator criticalityCalculator,
                                  DroolsRuleService droolsRuleService,
                                  PlaybookLoaderService playbookLoaderService,
                                  ClientRuleRepository clientRuleRepository) {
        this.criticalityCalculator = criticalityCalculator;
        this.droolsRuleService = droolsRuleService;
        this.playbookLoaderService = playbookLoaderService;
        this.clientRuleRepository = clientRuleRepository;
    }

    @Override
    public DecisionResult evaluate(UUID alertId, String tenantId, String credentialType,
                                    Map<String, Object> actionMatrix,
                                    Map<String, Object> metadata) {
        log.info("Evaluating decision for alert {} tenant {} credentialType {}", alertId, tenantId, credentialType);

        CriticalityResult result = criticalityCalculator.calculateCriticality(
                tenantId, credentialType, actionMatrix, metadata
        );

        ClientRule activeRule = clientRuleRepository.findActiveByTenantId(tenantId).orElse(null);
        Integer evaluatedVersion = activeRule != null ? activeRule.getVersion() : null;

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
