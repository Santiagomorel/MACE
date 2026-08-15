package com.company.rotations.decision.domain;

import com.company.rotations.models.Severidad;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CriticalityResultTest {

    @Test
    void constructor_setsAllFields() {
        CriticalityResult result = new CriticalityResult(
                Severidad.CRITICO, Severidad.ALTO, Severidad.BAJO,
                "test rationale", "test-playbook", "max formula"
        );

        assertEquals(Severidad.CRITICO, result.getCalculatedCriticality());
        assertEquals(Severidad.ALTO, result.getPlaybookFloor());
        assertEquals(Severidad.BAJO, result.getClientRules());
        assertEquals("test rationale", result.getRationale());
        assertEquals("test-playbook", result.getPlaybookId());
        assertEquals("max formula", result.getCalculatedVia());
    }

    @Test
    void constructor_allBajo() {
        CriticalityResult result = new CriticalityResult(
                Severidad.BAJO, Severidad.BAJO, Severidad.BAJO,
                "no match", "none", "no_playbook_match"
        );

        assertEquals(Severidad.BAJO, result.getCalculatedCriticality());
        assertEquals(Severidad.BAJO, result.getPlaybookFloor());
        assertEquals(Severidad.BAJO, result.getClientRules());
        assertEquals("no match", result.getRationale());
        assertEquals("none", result.getPlaybookId());
        assertEquals("no_playbook_match", result.getCalculatedVia());
    }

    @Test
    void constructor_allCRITICO() {
        CriticalityResult result = new CriticalityResult(
                Severidad.CRITICO, Severidad.CRITICO, Severidad.CRITICO,
                "all critical", "critical-playbook", "max formula"
        );

        assertEquals(Severidad.CRITICO, result.getCalculatedCriticality());
        assertEquals(Severidad.CRITICO, result.getPlaybookFloor());
        assertEquals(Severidad.CRITICO, result.getClientRules());
    }

    @Test
    void calculatedCriticality_equalsMaxOfFloorAndRules() {
        // When playbookFloor is higher
        CriticalityResult r1 = new CriticalityResult(
                Severidad.CRITICO, Severidad.CRITICO, Severidad.BAJO,
                "rationale", "pb", "formula"
        );
        assertEquals(Severidad.CRITICO, r1.getCalculatedCriticality());

        // When clientRules is higher
        CriticalityResult r2 = new CriticalityResult(
                Severidad.CRITICO, Severidad.BAJO, Severidad.CRITICO,
                "rationale", "pb", "formula"
        );
        assertEquals(Severidad.CRITICO, r2.getCalculatedCriticality());
    }

    @Test
    void rationale_containsFormula() {
        CriticalityResult result = new CriticalityResult(
                Severidad.ALTO, Severidad.ALTO, Severidad.BAJO,
                "max(playbook_floor=ALTO, client_rules=BAJO) = ALTO", "pb", "formula"
        );

        assertTrue(result.getRationale().contains("max("));
        assertTrue(result.getRationale().contains("playbook_floor=ALTO"));
        assertTrue(result.getRationale().contains("client_rules=BAJO"));
        assertTrue(result.getRationale().contains("ALTO"));
    }

    @Test
    void calculatedVia_isConsistent() {
        CriticalityResult result = new CriticalityResult(
                Severidad.ALTO, Severidad.ALTO, Severidad.BAJO,
                "rationale", "pb", "max(playbook_floor, client_rules)"
        );

        assertEquals("max(playbook_floor, client_rules)", result.getCalculatedVia());
    }
}
