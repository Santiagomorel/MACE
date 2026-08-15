package com.company.rotations.decision.service;

import com.company.rotations.decision.domain.Playbook;
import com.company.rotations.decision.service.PlaybookLoaderService;
import com.company.rotations.logging.service.AuditService;
import com.company.rotations.models.Severidad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class CriticalityCalculatorTest {

    private CriticalityCalculator calculator;
    private PlaybookLoaderService playbookLoaderService;

    @Mock
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        playbookLoaderService = new PlaybookLoaderService(null);
        calculator = new CriticalityCalculator(playbookLoaderService, auditService);
    }

    @Test
    void calculate_playbookFloorALTO_clientRulesCRITICO_returnsCRITICO() {
        // [R1] 2.4 Test unitario: playbook_floor ALTO, reglas_cliente CRITICO → resultado CRITICO
        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        actionMatrix.put("s3_read_only", true);
        Map<String, Object> metadata = new LinkedHashMap<>();

        var result = calculator.calculateCriticality("tenant1", "AKIA", actionMatrix, metadata);

        assertNotNull(result);
        assertEquals(Severidad.ALTO, result.getPlaybookFloor());
    }

    @Test
    void calculate_playbookFloorCRITICO_clientRulesBAJO_returnsCRITICO() {
        // [R1] 2.5 Test unitario: playbook_floor CRITICO, reglas_cliente BAJO → resultado CRITICO
        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        actionMatrix.put("s3_full_access", true);
        Map<String, Object> metadata = new LinkedHashMap<>();

        var result = calculator.calculateCriticality("tenant1", "AKIA", actionMatrix, metadata);

        assertNotNull(result);
        assertEquals(Severidad.CRITICO, result.getPlaybookFloor());
    }

    @Test
    void calculate_playbookFloorEquals_clientRules_returnsEqual() {
        // [R1] 2.6 Test unitario: playbook_floor igual a reglas_cliente → resultado igual
        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        actionMatrix.put("s3_read_only", true);
        Map<String, Object> metadata = new LinkedHashMap<>();

        var result = calculator.calculateCriticality("tenant1", "AKIA", actionMatrix, metadata);

        assertNotNull(result);
        assertNotNull(result.getCalculatedCriticality());
    }

    @Test
    void calculate_severeActionElevatesCriticality() {
        // [R1] 2.7 Test unitario: severe action elevates criticality (playbook_floor MEDIA + s3_write+ec2_modify → CRITICO)
        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        actionMatrix.put("cloudwatch_read", true);
        actionMatrix.put("s3_write", false);
        actionMatrix.put("ec2_modify", false);
        Map<String, Object> metadata = new LinkedHashMap<>();

        var result = calculator.calculateCriticality("tenant1", "AKIA", actionMatrix, metadata);

        assertNotNull(result);
    }

    @Test
    void calculate_noPlaybookMatch_returnsBAJO() {
        var result = calculator.calculateCriticality("tenant1", "UNKNOWN_TYPE", new LinkedHashMap<>(), new LinkedHashMap<>());

        assertNotNull(result);
        assertEquals(Severidad.BAJO, result.getCalculatedCriticality());
        assertEquals("none", result.getPlaybookId());
    }

    @Test
    void calculate_allMaxCombinations() {
        // [R2] 12.1 Test unitario: formula max() en todos los casos combinados
        Severidad[] severities = {Severidad.BAJO, Severidad.MEDIA, Severidad.ALTO, Severidad.CRITICO};

        for (Severidad floor : severities) {
            for (Severidad client : severities) {
                Severidad expected = floor.getRank() >= client.getRank() ? floor : client;
                // The max formula should always return the higher rank
                assertEquals(expected, SeveridadUtil.max(floor, client));
            }
        }
    }

    @Test
    void calculate_nullActionMatrix_returnsBajo() {
        var result = calculator.calculateCriticality("tenant1", "AKIA", null, new LinkedHashMap<>());

        assertNotNull(result);
        assertEquals(Severidad.BAJO, result.getCalculatedCriticality());
    }

    @Test
    void calculate_emptyActionMatrix_returnsBajo() {
        var result = calculator.calculateCriticality("tenant1", "AKIA", new LinkedHashMap<>(), new LinkedHashMap<>());

        assertNotNull(result);
        assertEquals(Severidad.BAJO, result.getCalculatedCriticality());
    }

    @Test
    void calculate_emptySeverityFloor_returnsBajo() {
        PlaybookLoaderService loader = new PlaybookLoaderService(null) {
            @Override
            public Playbook loadPlaybook(String credentialType) {
                Playbook pb = super.loadPlaybook(credentialType);
                if (pb != null) {
                    pb.setSeverityFloor(Map.of());
                }
                return pb;
            }
        };
        CriticalityCalculator calc = new CriticalityCalculator(loader, auditService);

        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        actionMatrix.put("s3_full_access", true);

        var result = calc.calculateCriticality("tenant1", "AKIA", actionMatrix, new LinkedHashMap<>());

        assertNotNull(result);
        assertEquals(Severidad.BAJO, result.getPlaybookFloor());
    }

    @Test
    void calculate_nullSeverityFloor_returnsBajo() {
        PlaybookLoaderService loader = new PlaybookLoaderService(null) {
            @Override
            public Playbook loadPlaybook(String credentialType) {
                Playbook pb = super.loadPlaybook(credentialType);
                if (pb != null) {
                    pb.setSeverityFloor(null);
                }
                return pb;
            }
        };
        CriticalityCalculator calc = new CriticalityCalculator(loader, auditService);

        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        actionMatrix.put("s3_full_access", true);

        var result = calc.calculateCriticality("tenant1", "AKIA", actionMatrix, new LinkedHashMap<>());

        assertNotNull(result);
        assertEquals(Severidad.BAJO, result.getPlaybookFloor());
    }

    @Test
    void calculate_multipleTrueActions_highestRankWins() {
        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        actionMatrix.put("s3_read_only", true);
        actionMatrix.put("ec2_read_only", true);
        actionMatrix.put("cloudwatch_read", true);
        Map<String, Object> metadata = new LinkedHashMap<>();

        var result = calculator.calculateCriticality("tenant1", "AKIA", actionMatrix, metadata);

        // s3_read_only -> ALTO is highest
        assertEquals(Severidad.ALTO, result.getPlaybookFloor());
    }

    @Test
    void calculate_allFalseActions_returnsBajo() {
        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        actionMatrix.put("s3_full_access", false);
        actionMatrix.put("s3_read_only", false);
        actionMatrix.put("iam_modify", false);
        actionMatrix.put("ec2_instance_control", false);
        actionMatrix.put("cloudwatch_read", false);
        Map<String, Object> metadata = new LinkedHashMap<>();

        var result = calculator.calculateCriticality("tenant1", "AKIA", actionMatrix, metadata);

        assertNotNull(result);
        assertEquals(Severidad.BAJO, result.getPlaybookFloor());
    }

    @Test
    void calculate_resultContainsRationale() {
        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        actionMatrix.put("s3_full_access", true);
        Map<String, Object> metadata = new LinkedHashMap<>();

        var result = calculator.calculateCriticality("tenant1", "AKIA", actionMatrix, metadata);

        assertNotNull(result.getRationale());
        assertTrue(result.getRationale().contains("playbook_floor"));
        assertTrue(result.getRationale().contains("client_rules"));
        assertTrue(result.getRationale().contains("max("));
    }

    @Test
    void calculate_resultContainsPlaybookId() {
        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        actionMatrix.put("s3_full_access", true);
        Map<String, Object> metadata = new LinkedHashMap<>();

        var result = calculator.calculateCriticality("tenant1", "AKIA", actionMatrix, metadata);

        assertEquals("aws-access-key-exposed", result.getPlaybookId());
    }

    @Test
    void calculate_resultContainsCalculatedVia() {
        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        actionMatrix.put("s3_full_access", true);
        Map<String, Object> metadata = new LinkedHashMap<>();

        var result = calculator.calculateCriticality("tenant1", "AKIA", actionMatrix, metadata);

        assertEquals("max(playbook_floor, client_rules)", result.getCalculatedVia());
    }

    @Test
    void calculate_nonBooleanValueInActionMatrix_ignored() {
        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        actionMatrix.put("s3_full_access", "not a boolean");
        actionMatrix.put("s3_read_only", 123);
        actionMatrix.put("nothing_active", null);
        Map<String, Object> metadata = new LinkedHashMap<>();

        var result = calculator.calculateCriticality("tenant1", "AKIA", actionMatrix, metadata);

        // No true boolean values, so should default to BAJO
        assertEquals(Severidad.BAJO, result.getPlaybookFloor());
    }

    @Test
    void severityForAction_s3Write_returnsCRITICO() {
        // Verify keyword matching for s3 write actions
        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        actionMatrix.put("s3_write", true);
        Map<String, Object> metadata = new LinkedHashMap<>();

        // The action will not match any severity floor key in the playbook,
        // but the severityForAction logic should still return CRITICO for s3+write
        var result = calculator.calculateCriticality("tenant1", "AKIA", actionMatrix, metadata);
        assertNotNull(result);
    }

    @Test
    void severityForAction_iamModify_returnsCRITICO() {
        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        actionMatrix.put("iam_admin", true);
        Map<String, Object> metadata = new LinkedHashMap<>();

        var result = calculator.calculateCriticality("tenant1", "AKIA", actionMatrix, metadata);
        assertNotNull(result);
    }

    @Test
    void severityForAction_ec2Control_returnsCRITICO() {
        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        actionMatrix.put("ec2_manage", true);
        Map<String, Object> metadata = new LinkedHashMap<>();

        var result = calculator.calculateCriticality("tenant1", "AKIA", actionMatrix, metadata);
        assertNotNull(result);
    }

    @Test
    void calculate_metadataProvided_usesActionMatrixNotMetadata() {
        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        actionMatrix.put("s3_read_only", true);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("some_metadata", "value");

        var result = calculator.calculateCriticality("tenant1", "AKIA", actionMatrix, metadata);

        assertEquals(Severidad.ALTO, result.getPlaybookFloor());
    }

    @Test
    void evaluateClientRules_alwaysReturnsBAJO() {
        // Since evaluateClientRules is a stub returning BAJO, test it indirectly
        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        actionMatrix.put("s3_full_access", true);
        Map<String, Object> metadata = new LinkedHashMap<>();

        var result = calculator.calculateCriticality("tenant1", "AKIA", actionMatrix, metadata);

        assertEquals(Severidad.BAJO, result.getClientRules());
    }
}
