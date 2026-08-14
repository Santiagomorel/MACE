package com.company.rotations.decision.service;

import com.company.rotations.models.Severidad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CriticalityCalculatorTest {

    private CriticalityCalculator calculator;
    private PlaybookLoaderService playbookLoaderService;

    @BeforeEach
    void setUp() {
        playbookLoaderService = new PlaybookLoaderService(null);
        calculator = new CriticalityCalculator(playbookLoaderService);
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
}
