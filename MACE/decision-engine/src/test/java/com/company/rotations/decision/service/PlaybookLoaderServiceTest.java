package com.company.rotations.decision.service;

import com.company.rotations.decision.domain.Playbook;
import com.company.rotations.models.Severidad;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlaybookLoaderServiceTest {

    private final PlaybookLoaderService loader = new PlaybookLoaderService(null);

    @Test
    void loadDefault_playbookExists() {
        Playbook playbook = loader.loadPlaybookById("aws-access-key-exposed");

        assertNotNull(playbook);
        assertEquals("aws-access-key-exposed", playbook.getPlaybookId());
        assertEquals("1.0.0", playbook.getVersion());
        assertEquals(List.of("AKIA"), playbook.getCredentialTypes());
        assertNotNull(playbook.getSeverityFloor());
        assertFalse(playbook.getCanLowerFloor());
    }

    @Test
    void loadDefault_sessionTokenPlaybook() {
        Playbook playbook = loader.loadPlaybookById("aws-session-token-leaked");

        assertNotNull(playbook);
        assertEquals("aws-session-token-leaked", playbook.getPlaybookId());
        assertEquals(List.of("ASIA"), playbook.getCredentialTypes());
        Playbook.AutoRotate autoRotate = playbook.getAutoRotate();
        assertNotNull(autoRotate);
        assertFalse(autoRotate.getEnabled());
        assertNull(autoRotate.getMaxWindowMins());
    }

    @Test
    void loadDefault_rootCredentialsPlaybook() {
        Playbook playbook = loader.loadPlaybookById("aws-root-credentials-exposed");

        assertNotNull(playbook);
        assertEquals("aws-root-credentials-exposed", playbook.getPlaybookId());
        Map<String, Severidad> floor = playbook.getSeverityFloor();
        // All floors should be CRITICO
        for (Severidad sev : floor.values()) {
            assertEquals(Severidad.CRITICO, sev);
        }
    }

    @Test
    void loadDefault_iamRoleAssumptionPlaybook() {
        Playbook playbook = loader.loadPlaybookById("aws-iam-role-assumption-abuse");

        assertNotNull(playbook);
        assertEquals("aws-iam-role-assumption-abuse", playbook.getPlaybookId());
        assertEquals(List.of("AKIA", "ASIA"), playbook.getCredentialTypes());
    }

    @Test
    void loadPlaybookByCredentialType_akia() {
        Playbook playbook = loader.loadPlaybook("AKIA");

        assertNotNull(playbook);
        assertEquals("aws-access-key-exposed", playbook.getPlaybookId());
    }

    @Test
    void loadPlaybookByCredentialType_asia() {
        Playbook playbook = loader.loadPlaybook("ASIA");

        assertNotNull(playbook);
        assertEquals("aws-session-token-leaked", playbook.getPlaybookId());
    }

    @Test
    void loadPlaybookByCredentialType_root() {
        Playbook playbook = loader.loadPlaybook("ROOT_");

        assertNotNull(playbook);
        assertEquals("aws-root-credentials-exposed", playbook.getPlaybookId());
    }

    @Test
    void loadPlaybookByCredentialType_unknown() {
        Playbook playbook = loader.loadPlaybook("UNKNOWN");

        assertNull(playbook);
    }

    @Test
    void resolvePlaybookIdsByCredentialType_returnsCorrectList() {
        assertEquals(List.of("aws-access-key-exposed", "aws-iam-role-assumption-abuse"),
                loader.resolvePlaybookIdsByCredentialType("AKIA"));
        assertEquals(List.of("aws-session-token-leaked", "aws-iam-role-assumption-abuse"),
                loader.resolvePlaybookIdsByCredentialType("ASIA"));
        assertEquals(List.of("aws-root-credentials-exposed"),
                loader.resolvePlaybookIdsByCredentialType("ROOT_"));
    }

    @Test
    void validatePlaybook_throwsWhenMissingFields() {
        Playbook invalid = new Playbook();
        assertThrows(NullPointerException.class, () -> loader.validatePlaybook(invalid));

        Playbook playbook = new Playbook();
        playbook.setPlaybookId("test");
        playbook.setVersion("1.0.0");
        assertThrows(NullPointerException.class, () -> loader.validatePlaybook(playbook));
    }

    @Test
    void validatePlaybook_passesForValidPlaybook() {
        Playbook playbook = new Playbook();
        playbook.setPlaybookId("test");
        playbook.setVersion("1.0.0");
        playbook.setCredentialTypes(List.of("AKIA"));
        Map<String, Severidad> floor1 = new HashMap<>();
        floor1.put("s3_full_access", Severidad.CRITICO);
        playbook.setSeverityFloor(floor1);

        // Should not throw
        loader.validatePlaybook(playbook);
    }

    @Test
    void applyDefaults_setsAutoRotateDefaults() {
        Playbook playbook = new Playbook();
        playbook.setPlaybookId("test");
        playbook.setVersion("1.0.0");
        playbook.setCredentialTypes(List.of("AKIA"));
        Map<String, Severidad> floor2 = new HashMap<>();
        floor2.put("test", Severidad.BAJO);
        playbook.setSeverityFloor(floor2);

        loader.validatePlaybook(playbook);

        assertNotNull(playbook.getAutoRotate());
        assertFalse(playbook.getAutoRotate().getEnabled());
        assertNotNull(playbook.getAutoRotate().getMaxWindowMins());
        assertEquals(60, playbook.getAutoRotate().getMaxWindowMins());
    }

    @Test
    void cache_reusesLoadedPlaybook() {
        Playbook first = loader.loadPlaybookById("aws-access-key-exposed");
        Playbook second = loader.loadPlaybookById("aws-access-key-exposed");

        assertSame(first, second);
    }

    @Test
    void loadPlaybookById_unknownPlaybookId_returnsNull() {
        Playbook playbook = loader.loadPlaybookById("non-existent-playbook");

        assertNull(playbook);
    }

    @Test
    void validatePlaybook_throwsWhenEmptyCredentialTypes() {
        Playbook playbook = new Playbook();
        playbook.setPlaybookId("test");
        playbook.setVersion("1.0.0");
        playbook.setCredentialTypes(List.of());
        Map<String, Severidad> floor3 = new HashMap<>();
        floor3.put("s3_full_access", Severidad.CRITICO);
        playbook.setSeverityFloor(floor3);

        assertThrows(IllegalArgumentException.class, () -> loader.validatePlaybook(playbook));
    }

    @Test
    void validatePlaybook_throwsWhenEmptySeverityFloor() {
        Playbook playbook = new Playbook();
        playbook.setPlaybookId("test");
        playbook.setVersion("1.0.0");
        playbook.setCredentialTypes(List.of("AKIA"));
        Map<String, Severidad> floor4 = new HashMap<>();
        playbook.setSeverityFloor(floor4);

        assertThrows(IllegalArgumentException.class, () -> loader.validatePlaybook(playbook));
    }

    @Test
    void applyDefaults_withNonNullAutoRotate_setsMissingFields() {
        Playbook playbook = new Playbook();
        playbook.setPlaybookId("test");
        playbook.setVersion("1.0.0");
        playbook.setCredentialTypes(List.of("AKIA"));
        Map<String, Severidad> floor5 = new HashMap<>();
        floor5.put("test", Severidad.BAJO);
        playbook.setSeverityFloor(floor5);

        Playbook.AutoRotate autoRotate = new Playbook.AutoRotate();
        autoRotate.setEnabled(true);
        playbook.setAutoRotate(autoRotate);

        loader.validatePlaybook(playbook);

        assertNotNull(playbook.getAutoRotate());
        assertEquals(true, playbook.getAutoRotate().getEnabled());
        assertNotNull(playbook.getAutoRotate().getMaxWindowMins());
        assertEquals(60, playbook.getAutoRotate().getMaxWindowMins());
    }

    @Test
    void applyDefaults_withNullEnabled_setsDefault() {
        Playbook playbook = new Playbook();
        playbook.setPlaybookId("test");
        playbook.setVersion("1.0.0");
        playbook.setCredentialTypes(List.of("AKIA"));
        Map<String, Severidad> floor6 = new HashMap<>();
        floor6.put("test", Severidad.BAJO);
        playbook.setSeverityFloor(floor6);

        Playbook.AutoRotate autoRotate = new Playbook.AutoRotate();
        autoRotate.setEnabled(null);
        autoRotate.setMaxWindowMins(30);
        playbook.setAutoRotate(autoRotate);

        loader.validatePlaybook(playbook);

        assertEquals(false, playbook.getAutoRotate().getEnabled());
        assertEquals(30, playbook.getAutoRotate().getMaxWindowMins());
    }

    @Test
    void applyDefaults_nullAutoRotate_createsNew() {
        Playbook playbook = new Playbook();
        playbook.setPlaybookId("test");
        playbook.setVersion("1.0.0");
        playbook.setCredentialTypes(List.of("AKIA"));
        Map<String, Severidad> floor7 = new HashMap<>();
        floor7.put("test", Severidad.BAJO);
        playbook.setSeverityFloor(floor7);
        playbook.setAutoRotate(null);

        loader.validatePlaybook(playbook);

        assertNotNull(playbook.getAutoRotate());
        assertEquals(false, playbook.getAutoRotate().getEnabled());
        assertEquals(60, playbook.getAutoRotate().getMaxWindowMins());
    }

    @Test
    void applyDefaults_nullCanLowerFloor_setsFalse() {
        Playbook playbook = new Playbook();
        playbook.setPlaybookId("test");
        playbook.setVersion("1.0.0");
        playbook.setCredentialTypes(List.of("AKIA"));
        Map<String, Severidad> floor8 = new HashMap<>();
        floor8.put("test", Severidad.BAJO);
        playbook.setSeverityFloor(floor8);
        playbook.setCanLowerFloor(null);

        loader.validatePlaybook(playbook);

        assertEquals(false, playbook.getCanLowerFloor());
    }

    @Test
    void applyDefaults_nonNullCanLowerFloor_preservesValue() {
        Playbook playbook = new Playbook();
        playbook.setPlaybookId("test");
        playbook.setVersion("1.0.0");
        playbook.setCredentialTypes(List.of("AKIA"));
        Map<String, Severidad> floor9 = new HashMap<>();
        floor9.put("test", Severidad.BAJO);
        playbook.setSeverityFloor(floor9);
        playbook.setCanLowerFloor(true);

        loader.validatePlaybook(playbook);

        assertEquals(true, playbook.getCanLowerFloor());
    }

    @Test
    void resolvePlaybookIdsByCredentialType_caseInsensitive() {
        assertEquals(List.of("aws-access-key-exposed", "aws-iam-role-assumption-abuse"),
                loader.resolvePlaybookIdsByCredentialType("akia"));
        assertEquals(List.of("aws-access-key-exposed", "aws-iam-role-assumption-abuse"),
                loader.resolvePlaybookIdsByCredentialType("Akia"));
    }

    @Test
    void resolvePlaybookIdsByCredentialType_emptyForUnknown() {
        assertTrue(loader.resolvePlaybookIdsByCredentialType("GCP_SERVICE_ACCOUNT").isEmpty());
        assertTrue(loader.resolvePlaybookIdsByCredentialType("AZURE_MANAGED_IDENTITY").isEmpty());
    }

    @Test
    void loadPlaybook_gcpCredentialType_returnsNull() {
        Playbook playbook = loader.loadPlaybook("GCP_SERVICE_ACCOUNT");

        assertNull(playbook);
    }

    @Test
    void loadPlaybook_multipleCredentialTypes_firstMatchWins() {
        Playbook playbook = loader.loadPlaybook("AKIA");

        assertNotNull(playbook);
        assertEquals("aws-access-key-exposed", playbook.getPlaybookId());
    }

    @Test
    void defaultAccessKeyPlaybook_hasCorrectFloorValues() {
        Playbook playbook = loader.loadPlaybookById("aws-access-key-exposed");

        Map<String, Severidad> floor = playbook.getSeverityFloor();
        assertEquals(Severidad.CRITICO, floor.get("s3_full_access"));
        assertEquals(Severidad.ALTO, floor.get("s3_read_only"));
        assertEquals(Severidad.CRITICO, floor.get("iam_modify"));
        assertEquals(Severidad.CRITICO, floor.get("ec2_instance_control"));
        assertEquals(Severidad.MEDIA, floor.get("cloudwatch_read"));
        assertEquals(Severidad.BAJO, floor.get("nothing_active"));
    }

    @Test
    void defaultSessionTokenPlaybook_hasCorrectFloorValues() {
        Playbook playbook = loader.loadPlaybookById("aws-session-token-leaked");

        Map<String, Severidad> floor = playbook.getSeverityFloor();
        assertEquals(Severidad.CRITICO, floor.get("assumed_role_admin_full_access"));
        assertEquals(Severidad.ALTO, floor.get("assumed_role_s3_read_write"));
        assertEquals(Severidad.ALTO, floor.get("assumed_role_ec2_manage"));
        assertEquals(Severidad.MEDIA, floor.get("assumed_role_read_only"));
        assertEquals(Severidad.BAJO, floor.get("expired_within_1h"));
    }

    @Test
    void defaultIamRolePlaybook_hasCorrectFloorValues() {
        Playbook playbook = loader.loadPlaybookById("aws-iam-role-assumption-abuse");

        Map<String, Severidad> floor = playbook.getSeverityFloor();
        assertEquals(Severidad.CRITICO, floor.get("cross_account_assume_untrusted_trust"));
        assertEquals(Severidad.CRITICO, floor.get("admin_role_assumed_from_regular_user"));
        assertEquals(Severidad.CRITICO, floor.get("sensitive_data_role_from_external_entity"));
        assertEquals(Severidad.MEDIA, floor.get("regular_role_from_internal_source_verified"));
        assertEquals(Severidad.BAJO, floor.get("orphaned_role_no_attached_policies"));
    }

    @Test
    void defaultPlaybooks_allHaveCanLowerFloorFalse() {
        String[] ids = {"aws-access-key-exposed", "aws-session-token-leaked",
                        "aws-root-credentials-exposed", "aws-iam-role-assumption-abuse"};

        for (String id : ids) {
            Playbook playbook = loader.loadPlaybookById(id);
            assertNotNull(playbook);
            assertFalse(playbook.getCanLowerFloor());
        }
    }

    @Test
    void defaultPlaybooks_allHaveAutoRotateSet() {
        String[] ids = {"aws-access-key-exposed", "aws-session-token-leaked",
                        "aws-root-credentials-exposed", "aws-iam-role-assumption-abuse"};

        for (String id : ids) {
            Playbook playbook = loader.loadPlaybookById(id);
            assertNotNull(playbook);
            assertNotNull(playbook.getAutoRotate());
            assertNotNull(playbook.getAutoRotate().getEnabled());
        }
    }

    @Test
    void defaultAccessKeyPlaybook_autoRotateEnabled() {
        Playbook playbook = loader.loadPlaybookById("aws-access-key-exposed");

        assertNotNull(playbook.getAutoRotate());
        assertEquals(true, playbook.getAutoRotate().getEnabled());
        assertEquals(15, playbook.getAutoRotate().getMaxWindowMins());
    }

    @Test
    void defaultRootPlaybook_allFloorsCritic() {
        Playbook playbook = loader.loadPlaybookById("aws-root-credentials-exposed");

        for (Severidad sev : playbook.getSeverityFloor().values()) {
            assertEquals(Severidad.CRITICO, sev);
        }
    }

    @Test
    void loadPlaybookById_cachingWorksCorrectly() {
        Playbook first = loader.loadPlaybookById("aws-access-key-exposed");
        Playbook cached = loader.loadPlaybookById("aws-access-key-exposed");

        assertSame(first, cached);

        // Different ID should be a different object
        Playbook different = loader.loadPlaybookById("aws-session-token-leaked");
        assertNotSame(first, different);
    }
}
