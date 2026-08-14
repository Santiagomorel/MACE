package com.company.rotations.decision.service;

import com.company.rotations.decision.domain.DiscoveredPermissions;
import com.company.rotations.models.Severidad;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DroolsRuleGeneratorTest {

    private final DroolsRuleGenerator generator = new DroolsRuleGenerator();

    @Test
    void generate_returnsValidDrlContent() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", true, false, true, false, false, false, false
        );

        String drl = generator.generate("tenant1", permissions);

        assertNotNull(drl);
        assertTrue(drl.startsWith("package com.security.rules.tenant1"));
        assertTrue(drl.contains("no-loop true"));
        assertTrue(drl.contains("lock-on-active true"));
        assertTrue(drl.contains("agenda-group"));
    }

    @Test
    void generate_oneRulePerPermission() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", true, true, false, false, false, false, false
        );

        String drl = generator.generate("tenant1", permissions);

        assertTrue(drl.contains("s3_full_access"));
        assertTrue(drl.contains("s3_read_only"));
    }

    @Test
    void generate_agendaGroupPerTenant() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant-abc", true, false, false, false, false, false, false
        );

        String drl = generator.generate("tenant-abc", permissions);

        assertTrue(drl.contains("agenda-group \"rules_tenant-abc\""));
    }

    @Test
    void generate_nothingActiveProducesBajoRule() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", false, false, false, false, false, false, true
        );

        String drl = generator.generate("tenant1", permissions);

        assertTrue(drl.contains("nothing_active"));
        assertTrue(drl.contains("Severidad.BAJO"));
    }

    @Test
    void generate_drlSizeWithinLimit() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", true, true, true, true, true, true, false
        );

        String drl = generator.generate("tenant1", permissions);

        int size = drl.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        // Typical DRL should be well under 50KB
        assertTrue(size < 51200, "DRL size " + size + " should be under 50KB");
    }

    @Test
    void discoveredPermissions_toSeverityFloor() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", true, true, true, false, false, false, false
        );

        var floor = permissions.toSeverityFloor();

        assertEquals(Severidad.CRITICO, floor.get("s3_full_access"));
        assertEquals(Severidad.ALTO, floor.get("s3_read_only"));
        assertEquals(Severidad.CRITICO, floor.get("iam_modify"));
    }

    @Test
    void discoveredPermissions_getHighestPermission() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", false, true, false, false, true, false, false
        );

        assertEquals("ALTO", permissions.getHighestPermission());
    }

    @Test
    void discoveredPermissions_nothingActiveReturnsBajo() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", false, false, false, false, false, false, true
        );

        assertEquals("BAJO", permissions.getHighestPermission());
    }
}
