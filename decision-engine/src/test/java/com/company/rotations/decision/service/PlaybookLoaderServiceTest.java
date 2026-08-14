package com.company.rotations.decision.service;

import com.company.rotations.decision.domain.Playbook;
import com.company.rotations.models.Severidad;
import org.junit.jupiter.api.Test;

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
        playbook.setSeverityFloor(Map.of("s3_full_access", Severidad.CRITICO));

        // Should not throw
        loader.validatePlaybook(playbook);
    }

    @Test
    void applyDefaults_setsAutoRotateDefaults() {
        Playbook playbook = new Playbook();
        playbook.setPlaybookId("test");
        playbook.setVersion("1.0.0");
        playbook.setCredentialTypes(List.of("AKIA"));
        playbook.setSeverityFloor(Map.of("test", Severidad.BAJO));

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
}
