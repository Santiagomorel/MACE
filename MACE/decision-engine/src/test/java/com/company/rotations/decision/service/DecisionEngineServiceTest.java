package com.company.rotations.decision.service;

import com.company.rotations.decision.domain.CriticalityResult;
import com.company.rotations.decision.domain.Playbook;
import com.company.rotations.logging.service.AuditService;
import com.company.rotations.models.ClientRule;
import com.company.rotations.decision.repository.ClientRuleRepository;
import com.company.rotations.models.Severidad;
import com.company.rotations.spi.DecisionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DecisionEngineServiceTest {

    @Mock
    private CriticalityCalculator criticalityCalculator;

    @Mock
    private DroolsRuleService droolsRuleService;

    @Mock
    private PlaybookLoaderService playbookLoaderService;

    @Mock
    private ClientRuleRepository clientRuleRepository;

    @Mock
    private AuditService auditService;

    private DecisionEngineService service;

    @BeforeEach
    void setUp() {
        service = new DecisionEngineService(
                criticalityCalculator, droolsRuleService,
                playbookLoaderService, clientRuleRepository, auditService
        );
    }

    @Test
    void evaluate_fullPipeline_returnsDecisionResult() {
        UUID alertId = UUID.randomUUID();
        String tenantId = "tenant1";
        String credentialType = "AKIA";
        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        actionMatrix.put("s3_full_access", true);
        Map<String, Object> metadata = new LinkedHashMap<>();

        CriticalityResult critResult = new CriticalityResult(
                Severidad.CRITICO, Severidad.CRITICO, Severidad.BAJO,
                "max(playbook_floor=CRITICO, client_rules=BAJO) = CRITICO",
                "aws-access-key-exposed", "max(playbook_floor, client_rules)"
        );
        when(criticalityCalculator.calculateCriticality(tenantId, credentialType, actionMatrix, metadata))
                .thenReturn(critResult);
        when(clientRuleRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.empty());
        when(playbookLoaderService.loadPlaybookById("aws-access-key-exposed")).thenReturn(createTestPlaybook());

        DecisionEngine.DecisionResult result = service.evaluate(
                alertId, tenantId, credentialType, actionMatrix, metadata
        );

        assertNotNull(result);
        assertEquals(Severidad.CRITICO, result.getSeverity());
        assertEquals("aws-access-key-exposed", result.getPlaybookId());
        assertEquals("max(playbook_floor, client_rules)", result.getCalculatedVia());
        assertNull(result.getEvaluatedRuleVersion());

        verify(auditService).logRuleEvaluated(anyMap());
        verify(criticalityCalculator).calculateCriticality(tenantId, credentialType, actionMatrix, metadata);
    }

    @Test
    void evaluate_withActiveRule_includesVersion() {
        UUID alertId = UUID.randomUUID();
        String tenantId = "tenant1";
        String credentialType = "AKIA";
        Map<String, Object> actionMatrix = Map.of("s3_full_access", true);
        Map<String, Object> metadata = new LinkedHashMap<>();

        CriticalityResult critResult = new CriticalityResult(
                Severidad.CRITICO, Severidad.CRITICO, Severidad.BAJO,
                "max(playbook_floor=CRITICO, client_rules=BAJO) = CRITICO",
                "aws-access-key-exposed", "max(playbook_floor, client_rules)"
        );
        when(criticalityCalculator.calculateCriticality(tenantId, credentialType, actionMatrix, metadata))
                .thenReturn(critResult);

        ClientRule rule = new ClientRule(
                UUID.randomUUID(), tenantId, 5, new byte[0], "aws-access-key-exposed"
        );
        when(clientRuleRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.of(rule));
        when(playbookLoaderService.loadPlaybookById("aws-access-key-exposed")).thenReturn(createTestPlaybook());

        DecisionEngine.DecisionResult result = service.evaluate(
                alertId, tenantId, credentialType, actionMatrix, metadata
        );

        assertNotNull(result);
        assertEquals(5, result.getEvaluatedRuleVersion());
    }

    @Test
    void evaluate_nullActionMatrix_includesZeroSize() {
        UUID alertId = UUID.randomUUID();
        String tenantId = "tenant1";
        String credentialType = "AKIA";
        Map<String, Object> metadata = new LinkedHashMap<>();

        CriticalityResult critResult = new CriticalityResult(
                Severidad.BAJO, Severidad.BAJO, Severidad.BAJO,
                "No playbook matched", "none", "no_playbook_match"
        );
        when(criticalityCalculator.calculateCriticality(tenantId, credentialType, null, metadata))
                .thenReturn(critResult);
        when(clientRuleRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.empty());

        DecisionEngine.DecisionResult result = service.evaluate(
                alertId, tenantId, credentialType, null, metadata
        );

        assertNotNull(result);
        assertEquals(Severidad.BAJO, result.getSeverity());
    }

    @Test
    void evaluate_auditServiceFailure_doesNotThrow() {
        UUID alertId = UUID.randomUUID();
        String tenantId = "tenant1";
        String credentialType = "AKIA";
        Map<String, Object> actionMatrix = Map.of("s3_full_access", true);
        Map<String, Object> metadata = new LinkedHashMap<>();

        CriticalityResult critResult = new CriticalityResult(
                Severidad.BAJO, Severidad.BAJO, Severidad.BAJO,
                "No playbook matched", "none", "no_playbook_match"
        );
        when(criticalityCalculator.calculateCriticality(tenantId, credentialType, actionMatrix, metadata))
                .thenReturn(critResult);
        when(clientRuleRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.empty());

        doThrow(new RuntimeException("audit service down")).when(auditService).logRuleEvaluated(anyMap());

        // Should not throw despite audit failures
        assertDoesNotThrow(() ->
                service.evaluate(alertId, tenantId, credentialType, actionMatrix, metadata)
        );
    }

    @Test
    void evaluate_noPlaybookMatch_returnsBajo() {
        UUID alertId = UUID.randomUUID();
        String tenantId = "tenant1";
        String credentialType = "UNKNOWN_TYPE";
        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        Map<String, Object> metadata = new LinkedHashMap<>();

        CriticalityResult critResult = new CriticalityResult(
                Severidad.BAJO, Severidad.BAJO, Severidad.BAJO,
                "No playbook matched credential type UNKNOWN_TYPE",
                "none", "no_playbook_match"
        );
        when(criticalityCalculator.calculateCriticality(tenantId, credentialType, actionMatrix, metadata))
                .thenReturn(critResult);
        when(clientRuleRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.empty());

        DecisionEngine.DecisionResult result = service.evaluate(
                alertId, tenantId, credentialType, actionMatrix, metadata
        );

        assertNotNull(result);
        assertEquals(Severidad.BAJO, result.getSeverity());
        assertEquals("none", result.getPlaybookId());
        assertTrue(result.getComplianceTags().isEmpty());
    }

    @Test
    void evaluate_complianceTagsExtracted() {
        UUID alertId = UUID.randomUUID();
        String tenantId = "tenant1";
        String credentialType = "AKIA";
        Map<String, Object> actionMatrix = Map.of("s3_full_access", true);
        Map<String, Object> metadata = new LinkedHashMap<>();

        Playbook playbook = createTestPlaybook();
        Playbook.ComplianceTag tag = new Playbook.ComplianceTag();
        tag.setSource("SOC2");
        tag.setControlDescription("Controlled access to AWS credentials");
        playbook.setComplianceTags(List.of(tag));

        CriticalityResult critResult = new CriticalityResult(
                Severidad.CRITICO, Severidad.CRITICO, Severidad.BAJO,
                "max(playbook_floor=CRITICO, client_rules=BAJO) = CRITICO",
                "aws-access-key-exposed", "max(playbook_floor, client_rules)"
        );
        when(criticalityCalculator.calculateCriticality(tenantId, credentialType, actionMatrix, metadata))
                .thenReturn(critResult);
        when(clientRuleRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.empty());
        when(playbookLoaderService.loadPlaybookById("aws-access-key-exposed")).thenReturn(playbook);

        DecisionEngine.DecisionResult result = service.evaluate(
                alertId, tenantId, credentialType, actionMatrix, metadata
        );

        assertNotNull(result.getComplianceTags());
        assertFalse(result.getComplianceTags().isEmpty());
        assertEquals("Controlled access to AWS credentials", result.getComplianceTags().get("SOC2"));
    }

    @Test
    void evaluate_extractComplianceTags_nullPlaybook_returnsEmpty() {
        UUID alertId = UUID.randomUUID();
        String tenantId = "tenant1";
        String credentialType = "AKIA";
        Map<String, Object> actionMatrix = Map.of("s3_full_access", true);
        Map<String, Object> metadata = new LinkedHashMap<>();

        CriticalityResult critResult = new CriticalityResult(
                Severidad.BAJO, Severidad.BAJO, Severidad.BAJO,
                "No playbook", "none", "no_playbook_match"
        );
        when(criticalityCalculator.calculateCriticality(tenantId, credentialType, actionMatrix, metadata))
                .thenReturn(critResult);
        when(clientRuleRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.empty());
        when(playbookLoaderService.loadPlaybookById("none")).thenReturn(null);

        DecisionEngine.DecisionResult result = service.evaluate(
                alertId, tenantId, credentialType, actionMatrix, metadata
        );

        assertTrue(result.getComplianceTags().isEmpty());
    }

    @Test
    void evaluate_decisionResultContainsAllFields() {
        UUID alertId = UUID.randomUUID();
        String tenantId = "tenant1";
        String credentialType = "AKIA";
        Map<String, Object> actionMatrix = Map.of("s3_full_access", true);
        Map<String, Object> metadata = new LinkedHashMap<>();

        ClientRule rule = new ClientRule(
                UUID.randomUUID(), tenantId, 3, new byte[0], "aws-access-key-exposed"
        );
        CriticalityResult critResult = new CriticalityResult(
                Severidad.ALTO, Severidad.ALTO, Severidad.BAJO,
                "max(playbook_floor=ALTO, client_rules=BAJO) = ALTO",
                "aws-access-key-exposed", "max(playbook_floor, client_rules)"
        );
        when(criticalityCalculator.calculateCriticality(tenantId, credentialType, actionMatrix, metadata))
                .thenReturn(critResult);
        when(clientRuleRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.of(rule));
        when(playbookLoaderService.loadPlaybookById("aws-access-key-exposed")).thenReturn(createTestPlaybook());

        DecisionEngine.DecisionResult result = service.evaluate(
                alertId, tenantId, credentialType, actionMatrix, metadata
        );

        assertEquals(Severidad.ALTO, result.getSeverity());
        assertEquals("max(playbook_floor=ALTO, client_rules=BAJO) = ALTO", result.getRationale());
        assertEquals("aws-access-key-exposed", result.getPlaybookId());
        assertEquals("max(playbook_floor, client_rules)", result.getCalculatedVia());
        assertEquals(3, result.getEvaluatedRuleVersion());
    }

    @Test
    void evaluate_actionMatrixSize_logged() {
        UUID alertId = UUID.randomUUID();
        String tenantId = "tenant1";
        String credentialType = "AKIA";
        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        actionMatrix.put("s3_full_access", true);
        actionMatrix.put("iam_modify", true);
        actionMatrix.put("ec2_read_only", true);
        Map<String, Object> metadata = new LinkedHashMap<>();

        CriticalityResult critResult = new CriticalityResult(
                Severidad.CRITICO, Severidad.CRITICO, Severidad.BAJO,
                "max(playbook_floor=CRITICO, client_rules=BAJO) = CRITICO",
                "aws-access-key-exposed", "max(playbook_floor, client_rules)"
        );
        when(criticalityCalculator.calculateCriticality(tenantId, credentialType, actionMatrix, metadata))
                .thenReturn(critResult);
        when(clientRuleRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.empty());
        when(playbookLoaderService.loadPlaybookById("aws-access-key-exposed")).thenReturn(createTestPlaybook());

        service.evaluate(alertId, tenantId, credentialType, actionMatrix, metadata);

        verify(auditService, atLeastOnce()).logRuleEvaluated(argThat(data -> {
            Object size = data.get("action_matrix_size");
            return size != null && Integer.parseInt(size.toString()) == 3;
        }));
    }

    private Playbook createTestPlaybook() {
        Playbook playbook = new Playbook();
        playbook.setPlaybookId("aws-access-key-exposed");
        playbook.setVersion("1.0.0");
        playbook.setCredentialTypes(List.of("AKIA"));
        Map<String, Severidad> floor = new LinkedHashMap<>();
        floor.put("s3_full_access", Severidad.CRITICO);
        floor.put("s3_read_only", Severidad.ALTO);
        floor.put("iam_modify", Severidad.CRITICO);
        floor.put("ec2_instance_control", Severidad.CRITICO);
        floor.put("cloudwatch_read", Severidad.MEDIA);
        floor.put("nothing_active", Severidad.BAJO);
        playbook.setSeverityFloor(floor);
        Playbook.AutoRotate autoRotate = new Playbook.AutoRotate();
        autoRotate.setEnabled(true);
        autoRotate.setMaxWindowMins(15);
        playbook.setAutoRotate(autoRotate);
        playbook.setCanLowerFloor(false);
        return playbook;
    }
}
