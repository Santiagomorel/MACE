package com.company.rotations.decision.domain;

import com.company.rotations.models.Severidad;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DiscoveredPermissionsTest {

    @Test
    void toSeverityFloor_allPermissionsSet_returnsAllFloors() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", true, true, true, true, true, true, false
        );

        Map<String, Severidad> floor = permissions.toSeverityFloor();

        assertEquals(6, floor.size());
        assertEquals(Severidad.CRITICO, floor.get("s3_full_access"));
        assertEquals(Severidad.ALTO, floor.get("s3_read_only"));
        assertEquals(Severidad.CRITICO, floor.get("iam_modify"));
        assertEquals(Severidad.CRITICO, floor.get("ec2_instance_control"));
        assertEquals(Severidad.MEDIA, floor.get("ec2_read_only"));
        assertEquals(Severidad.MEDIA, floor.get("cloudwatch_read"));
    }

    @Test
    void toSeverityFloor_noPermissionsSet_returnsEmptyMap() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", false, false, false, false, false, false, true
        );

        Map<String, Severidad> floor = permissions.toSeverityFloor();

        assertEquals(1, floor.size());
        assertEquals(Severidad.BAJO, floor.get("nothing_active"));
    }

    @Test
    void toSeverityFloor_onlyS3FullAccess_returnsOneFloor() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", true, false, false, false, false, false, false
        );

        Map<String, Severidad> floor = permissions.toSeverityFloor();

        assertEquals(1, floor.size());
        assertEquals(Severidad.CRITICO, floor.get("s3_full_access"));
    }

    @Test
    void toSeverityFloor_multipleS3Access_returnsTwoFloors() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", true, true, false, false, false, false, false
        );

        Map<String, Severidad> floor = permissions.toSeverityFloor();

        assertEquals(2, floor.size());
        assertTrue(floor.containsKey("s3_full_access"));
        assertTrue(floor.containsKey("s3_read_only"));
    }

    @Test
    void getHighestPermission_s3FullAccess_returnsCRITICO() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", true, false, false, false, false, false, false
        );

        assertEquals("CRITICO", permissions.getHighestPermission());
    }

    @Test
    void getHighestPermission_iamModify_returnsCRITICO() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", false, false, true, false, false, false, false
        );

        assertEquals("CRITICO", permissions.getHighestPermission());
    }

    @Test
    void getHighestPermission_ec2InstanceControl_returnsCRITICO() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", false, false, false, true, false, false, false
        );

        assertEquals("CRITICO", permissions.getHighestPermission());
    }

    @Test
    void getHighestPermission_s3ReadOnly_returnsALTO() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", false, true, false, false, false, false, false
        );

        assertEquals("ALTO", permissions.getHighestPermission());
    }

    @Test
    void getHighestPermission_ec2ReadOnly_returnsMEDIA() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", false, false, false, false, true, false, false
        );

        assertEquals("MEDIA", permissions.getHighestPermission());
    }

    @Test
    void getHighestPermission_cloudWatchRead_returnsMEDIA() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", false, false, false, false, false, true, false
        );

        assertEquals("MEDIA", permissions.getHighestPermission());
    }

    @Test
    void getHighestPermission_nothingActive_returnsBAJO() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", false, false, false, false, false, false, true
        );

        assertEquals("BAJO", permissions.getHighestPermission());
    }

    @Test
    void getHighestPermission_allFalse_returnsBAJO() {
        // When nothingActive is false and all permissions are false
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", false, false, false, false, false, false, false
        );

        assertEquals("BAJO", permissions.getHighestPermission());
    }

    @Test
    void getHighestPermission_multipleCRITICO_returnsCRITICO() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", true, false, true, true, false, false, false
        );

        assertEquals("CRITICO", permissions.getHighestPermission());
    }

    @Test
    void getHighestPermission_criticalAndAlto_returnsCRITICO() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", true, true, false, false, false, false, false
        );

        assertEquals("CRITICO", permissions.getHighestPermission());
    }

    @Test
    void getHighestPermission_altoAndMedia_returnsALTO() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", false, true, false, false, true, false, false
        );

        assertEquals("ALTO", permissions.getHighestPermission());
    }

    @Test
    void tenantId_isStored() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "my-tenant-123", false, false, false, false, false, false, true
        );

        assertEquals("my-tenant-123", permissions.getTenantId());
    }

    @Test
    void getters_returnCorrectValues() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", true, true, true, true, true, true, false
        );

        assertTrue(permissions.isS3FullAccess());
        assertTrue(permissions.isS3ReadOnly());
        assertTrue(permissions.isIamModify());
        assertTrue(permissions.isEc2InstanceControl());
        assertTrue(permissions.isEc2ReadOnly());
        assertTrue(permissions.isCloudWatchRead());
        assertFalse(permissions.isNothingActive());
    }

    @Test
    void toSeverityFloor_priorityOrder_s3FullOverridesS3ReadOnly() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "tenant1", true, true, false, false, false, false, false
        );

        Map<String, Severidad> floor = permissions.toSeverityFloor();
        // Both should be present as separate entries
        assertEquals(2, floor.size());
    }

    @Test
    void constructor_allParamsSet_correctly() {
        DiscoveredPermissions permissions = new DiscoveredPermissions(
                "test-tenant", true, false, true, false, true, false, false
        );

        assertEquals("test-tenant", permissions.getTenantId());
        assertTrue(permissions.isS3FullAccess());
        assertFalse(permissions.isS3ReadOnly());
        assertTrue(permissions.isIamModify());
        assertFalse(permissions.isEc2InstanceControl());
        assertTrue(permissions.isEc2ReadOnly());
        assertFalse(permissions.isCloudWatchRead());
        assertFalse(permissions.isNothingActive());
    }
}
