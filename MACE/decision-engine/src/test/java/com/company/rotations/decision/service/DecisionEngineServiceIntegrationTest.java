package com.company.rotations.decision.service;

import com.company.rotations.decision.domain.Playbook;
import com.company.rotations.logging.service.AuditService;
import com.company.rotations.models.Severidad;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DecisionEngineServiceIntegrationTest {

    private final PlaybookLoaderService playbookLoader = new PlaybookLoaderService(null);
    private final CriticalityCalculator calculator = new CriticalityCalculator(playbookLoader, mock(AuditService.class));

    @Test
    void fullPipeline_akiaKey_s3FullAccess() {
        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        actionMatrix.put("s3_full_access", true);
        Map<String, Object> metadata = new LinkedHashMap<>();

        var result = calculator.calculateCriticality("tenant1", "AKIA", actionMatrix, metadata);

        assertNotNull(result);
        assertEquals(Severidad.CRITICO, result.getCalculatedCriticality());
        assertEquals(Severidad.CRITICO, result.getPlaybookFloor());
        assertTrue(result.getRationale().contains("max("));
        assertEquals("aws-access-key-exposed", result.getPlaybookId());
    }

    @Test
    void fullPipeline_asiaToken_readOnly() {
        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        actionMatrix.put("assumed_role_read_only", true);
        Map<String, Object> metadata = new LinkedHashMap<>();

        var result = calculator.calculateCriticality("tenant1", "ASIA", actionMatrix, metadata);

        assertNotNull(result);
        assertEquals(Severidad.MEDIA, result.getPlaybookFloor());
        assertEquals("aws-session-token-leaked", result.getPlaybookId());
    }

    @Test
    void fullPipeline_rootCredentials_anyResource() {
        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        actionMatrix.put("any_resource_active", true);
        Map<String, Object> metadata = new LinkedHashMap<>();

        var result = calculator.calculateCriticality("tenant1", "ROOT_", actionMatrix, metadata);

        assertNotNull(result);
        assertEquals(Severidad.CRITICO, result.getCalculatedCriticality());
        assertEquals("aws-root-credentials-exposed", result.getPlaybookId());
    }

    @Test
    void fullPipeline_multiplePermissions_highestWins() {
        Map<String, Object> actionMatrix = new LinkedHashMap<>();
        actionMatrix.put("cloudwatch_read", true);
        actionMatrix.put("s3_full_access", true);
        actionMatrix.put("iam_modify", true);
        Map<String, Object> metadata = new LinkedHashMap<>();

        var result = calculator.calculateCriticality("tenant1", "AKIA", actionMatrix, metadata);

        // Highest permission determines the floor
        assertEquals(Severidad.CRITICO, result.getPlaybookFloor());
    }
}
