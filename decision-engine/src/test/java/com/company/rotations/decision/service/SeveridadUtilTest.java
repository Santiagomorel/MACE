package com.company.rotations.decision.service;

import com.company.rotations.models.Severidad;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SeveridadUtilTest {

    @Test
    void max_returnsHigherRank() {
        assertEquals(Severidad.ALTO, SeveridadUtil.max(Severidad.BAJO, Severidad.ALTO));
        assertEquals(Severidad.CRITICO, SeveridadUtil.max(Severidad.ALTO, Severidad.CRITICO));
        assertEquals(Severidad.CRITICO, SeveridadUtil.max(Severidad.CRITICO, Severidad.ALTO));
    }

    @Test
    void max_returnsEqualWhenSame() {
        assertEquals(Severidad.ALTO, SeveridadUtil.max(Severidad.ALTO, Severidad.ALTO));
        assertEquals(Severidad.CRITICO, SeveridadUtil.max(Severidad.CRITICO, Severidad.CRITICO));
    }

    @Test
    void max_handlesNull() {
        assertEquals(Severidad.ALTO, SeveridadUtil.max(null, Severidad.ALTO));
        assertEquals(Severidad.CRITICO, SeveridadUtil.max(Severidad.CRITICO, null));
        assertNull(SeveridadUtil.max(null, null));
    }

    @Test
    void max_playbookFloorCRITICO_clientRulesBAJO() {
        // Test: playbook_floor CRITICO, reglas_cliente BAJO → resultado CRITICO
        assertEquals(Severidad.CRITICO, SeveridadUtil.max(Severidad.CRITICO, Severidad.BAJO));
    }

    @Test
    void max_playbookFloorALTO_clientRulesCRITICO() {
        // Test: playbook_floor ALTO, reglas_cliente CRITICO → resultado CRITICO
        assertEquals(Severidad.CRITICO, SeveridadUtil.max(Severidad.ALTO, Severidad.CRITICO));
    }

    @Test
    void max_playbookFloorMEDIA_severeActionElevates() {
        // Test: playbook_floor MEDIA + s3_write+ec2_modify → CRITICO
        assertEquals(Severidad.CRITICO, SeveridadUtil.max(Severidad.MEDIA, Severidad.CRITICO));
    }

    @Test
    void toSalience_mapsCorrectly() {
        assertEquals(100, SeveridadUtil.toSalience(Severidad.CRITICO));
        assertEquals(80, SeveridadUtil.toSalience(Severidad.ALTO));
        assertEquals(60, SeveridadUtil.toSalience(Severidad.MEDIA));
        assertEquals(40, SeveridadUtil.toSalience(Severidad.BAJO));
    }

    @Test
    void fromSalience_mapsCorrectly() {
        assertEquals(Severidad.CRITICO, SeveridadUtil.fromSalience(100));
        assertEquals(Severidad.ALTO, SeveridadUtil.fromSalience(80));
        assertEquals(Severidad.MEDIA, SeveridadUtil.fromSalience(60));
        assertEquals(Severidad.BAJO, SeveridadUtil.fromSalience(40));
    }

    @Test
    void fromSalience_throwsForUnknown() {
        assertThrows(IllegalArgumentException.class, () -> SeveridadUtil.fromSalience(50));
    }
}
