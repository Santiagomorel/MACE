package com.company.rotations.decision.domain;

import com.company.rotations.models.Severidad;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlaybookTest {

    @Test
    void settersAndGetters_workCorrectly() {
        Playbook playbook = new Playbook();

        playbook.setPlaybookId("test-pb");
        assertEquals("test-pb", playbook.getPlaybookId());

        playbook.setVersion("2.0.0");
        assertEquals("2.0.0", playbook.getVersion());
    }

    @Test
    void credentialTypes_listOperations() {
        Playbook playbook = new Playbook();
        List<String> types = List.of("AKIA", "ASIA");
        playbook.setCredentialTypes(types);

        assertEquals(2, playbook.getCredentialTypes().size());
        assertTrue(playbook.getCredentialTypes().contains("AKIA"));
        assertTrue(playbook.getCredentialTypes().contains("ASIA"));
    }

    @Test
    void severityFloor_mapOperations() {
        Playbook playbook = new Playbook();
        Map<String, Severidad> floor = Map.of(
                "s3_full_access", Severidad.CRITICO,
                "s3_read_only", Severidad.ALTO
        );
        playbook.setSeverityFloor(floor);

        assertEquals(2, playbook.getSeverityFloor().size());
        assertEquals(Severidad.CRITICO, playbook.getSeverityFloor().get("s3_full_access"));
        assertEquals(Severidad.ALTO, playbook.getSeverityFloor().get("s3_read_only"));
    }

    @Test
    void autoRotate_nestedObject() {
        Playbook playbook = new Playbook();
        Playbook.AutoRotate autoRotate = new Playbook.AutoRotate();
        autoRotate.setEnabled(true);
        autoRotate.setMaxWindowMins(30);
        playbook.setAutoRotate(autoRotate);

        assertNotNull(playbook.getAutoRotate());
        assertEquals(true, playbook.getAutoRotate().getEnabled());
        assertEquals(30, playbook.getAutoRotate().getMaxWindowMins());
    }

    @Test
    void canLowerFloor_boolean() {
        Playbook playbook = new Playbook();
        playbook.setCanLowerFloor(true);

        assertEquals(true, playbook.getCanLowerFloor());

        playbook.setCanLowerFloor(false);
        assertEquals(false, playbook.getCanLowerFloor());
    }

    @Test
    void complianceTag_nestedObject() {
        Playbook.ComplianceTag tag = new Playbook.ComplianceTag();
        tag.setSource("SOC2");
        tag.setControlDescription("Test control");

        assertEquals("SOC2", tag.getSource());
        assertEquals("Test control", tag.getControlDescription());
    }

    @Test
    void actionOnExposure_nestedObject() {
        Playbook.ActionOnExposure action = new Playbook.ActionOnExposure();
        action.setActionType("rotate");
        action.setTarget("credentials");
        action.setPriorityOrder(1);

        assertEquals("rotate", action.getActionType());
        assertEquals("credentials", action.getTarget());
        assertEquals(1, action.getPriorityOrder());
    }

    @Test
    void credentialTargeted_nestedObject() {
        Playbook.CredentialTargeted targeted = new Playbook.CredentialTargeted();
        targeted.setCredentialType("AKIA");
        targeted.setDescription("AWS access key");

        assertEquals("AKIA", targeted.getCredentialType());
        assertEquals("AWS access key", targeted.getDescription());
    }

    @Test
    void conditions_nestedObject() {
        Playbook.Conditions conditions = new Playbook.Conditions();
        conditions.setProvider("aws");
        conditions.setDetectionSource("cloudtrail");

        assertEquals("aws", conditions.getProvider());
        assertEquals("cloudtrail", conditions.getDetectionSource());
    }

    @Test
    void complianceTags_listOperations() {
        Playbook playbook = new Playbook();
        Playbook.ComplianceTag tag1 = new Playbook.ComplianceTag();
        tag1.setSource("SOC2");
        Playbook.ComplianceTag tag2 = new Playbook.ComplianceTag();
        tag2.setSource("HIPAA");
        playbook.setComplianceTags(List.of(tag1, tag2));

        assertEquals(2, playbook.getComplianceTags().size());
    }

    @Test
    void actionsOnExposure_listOperations() {
        Playbook playbook = new Playbook();
        Playbook.ActionOnExposure action = new Playbook.ActionOnExposure();
        action.setActionType("notify");
        playbook.setActionsOnExposure(List.of(action));

        assertEquals(1, playbook.getActionsOnExposure().size());
        assertEquals("notify", playbook.getActionsOnExposure().get(0).getActionType());
    }

    @Test
    void credentialsTargeted_listOperations() {
        Playbook playbook = new Playbook();
        Playbook.CredentialTargeted ct = new Playbook.CredentialTargeted();
        ct.setCredentialType("AKIA");
        playbook.setCredentialsTargeted(List.of(ct));

        assertEquals(1, playbook.getCredentialsTargeted().size());
    }

    @Test
    void allNestedClassesCanBeNull() {
        Playbook playbook = new Playbook();

        assertNull(playbook.getCredentialTypes());
        assertNull(playbook.getSeverityFloor());
        assertNull(playbook.getAutoRotate());
        assertNull(playbook.getActionsOnExposure());
        assertNull(playbook.getComplianceTags());
        assertNull(playbook.getCredentialsTargeted());
        assertNull(playbook.getConditions());
        assertNull(playbook.getCanLowerFloor());
    }
}
