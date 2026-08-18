package com.company.rotations.decision.service;

import com.company.rotations.decision.domain.DiscoveredPermissions;
import com.company.rotations.models.ClientRule;
import com.company.rotations.models.Credential;
import com.company.rotations.decision.repository.ClientRuleRepository;
import com.company.rotations.decision.repository.CredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AwsMetadataDiscoveryServiceTest {

    @Mock
    private DroolsRuleService droolsRuleService;

    @Mock
    private DroolsRuleGenerator droolsRuleGenerator;

    @Mock
    private SemaphoreService semaphoreService;

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private ClientRuleRepository clientRuleRepository;

    private AwsMetadataDiscoveryService discoveryService;

    @BeforeEach
    void setUp() {
        discoveryService = new AwsMetadataDiscoveryService(
                droolsRuleService, droolsRuleGenerator, semaphoreService,
                credentialRepository, clientRuleRepository
        );
    }

    private void setDiscoveryEnabled(boolean enabled) throws NoSuchFieldException, IllegalAccessException {
        Field field = AwsMetadataDiscoveryService.class.getDeclaredField("discoveryEnabled");
        field.setAccessible(true);
        field.set(discoveryService, enabled);
    }



    @Test
    void discover_returnsDiscoveredPermissions() {
        String tenantId = "tenant1";
        Map<String, String> credentials = new HashMap<>();
        credentials.put("accessKey", "AKIA1234567890");
        credentials.put("secretKey", "secret123");
        credentials.put("region", "us-west-2");

        DiscoveredPermissions result = discoveryService.discover(tenantId, credentials);

        assertNotNull(result);
        assertEquals(tenantId, result.getTenantId());
        assertTrue(result.isNothingActive());
        assertFalse(result.isS3FullAccess());
        assertFalse(result.isS3ReadOnly());
        assertFalse(result.isIamModify());
        assertFalse(result.isEc2InstanceControl());
        assertFalse(result.isEc2ReadOnly());
        assertFalse(result.isCloudWatchRead());
    }

    @Test
    void discover_defaultsRegionToUsEast1() {
        String tenantId = "tenant1";
        Map<String, String> credentials = new HashMap<>();
        credentials.put("accessKey", "AKIA1234567890");
        credentials.put("secretKey", "secret123");

        DiscoveredPermissions result = discoveryService.discover(tenantId, credentials);

        assertNotNull(result);
        assertEquals(tenantId, result.getTenantId());
    }

    @Test
    void discoverAndRegenerate_normalFlow_callsAllSteps() {
        String tenantId = "tenant1";
        Map<String, String> credentials = new HashMap<>();
        credentials.put("accessKey", "AKIA1234567890");
        credentials.put("secretKey", "secret123");

        DiscoveredPermissions permissions = new DiscoveredPermissions(
                tenantId, true, false, false, false, false, false, false
        );
        when(droolsRuleGenerator.generate(eq(tenantId), any(DiscoveredPermissions.class)))
                .thenReturn("package com.test;\nrule \"test\"\n  salience 40\nwhen\nthen\nend\n");
        when(semaphoreService.acquireSemaphore("rule_generation_tenant1", 15 * 60, 30))
                .thenReturn(true);
        when(droolsRuleService.updateRulesForTenant(eq(tenantId), any(byte[].class), eq("auto-generated")))
                .thenReturn(true);

        assertDoesNotThrow(() -> discoveryService.discoverAndRegenerate(tenantId, credentials));

        verify(droolsRuleGenerator).generate(eq(tenantId), any(DiscoveredPermissions.class));
        verify(semaphoreService).acquireSemaphore("rule_generation_tenant1", 15 * 60, 30);
        verify(droolsRuleService).updateRulesForTenant(eq(tenantId), any(byte[].class), eq("auto-generated"));
        verify(droolsRuleService, never()).rollbackToLastValid(anyString());
    }

    @Test
    void discoverAndRegenerate_semaphoreFailed_skipsUpdate() {
        String tenantId = "tenant1";
        Map<String, String> credentials = new HashMap<>();
        credentials.put("accessKey", "AKIA1234567890");
        credentials.put("secretKey", "secret123");

        when(semaphoreService.acquireSemaphore("rule_generation_tenant1", 15 * 60, 30))
                .thenReturn(false);

        assertDoesNotThrow(() -> discoveryService.discoverAndRegenerate(tenantId, credentials));

        verify(semaphoreService).acquireSemaphore("rule_generation_tenant1", 15 * 60, 30);
        verify(droolsRuleService, never()).updateRulesForTenant(anyString(), any(byte[].class), anyString());
        verify(droolsRuleService, never()).rollbackToLastValid(anyString());
    }

    @Test
    void discoverAndRegenerate_updateFails_triggersRollback() {
        String tenantId = "tenant1";
        Map<String, String> credentials = new HashMap<>();
        credentials.put("accessKey", "AKIA1234567890");
        credentials.put("secretKey", "secret123");

        DiscoveredPermissions permissions = new DiscoveredPermissions(
                tenantId, true, false, false, false, false, false, false
        );
        when(droolsRuleGenerator.generate(eq(tenantId), any(DiscoveredPermissions.class)))
                .thenReturn("package com.test;\nrule \"test\"\n  salience 40\nwhen\nthen\nend\n");
        when(semaphoreService.acquireSemaphore("rule_generation_tenant1", 15 * 60, 30))
                .thenReturn(true);
        when(droolsRuleService.updateRulesForTenant(eq(tenantId), any(byte[].class), eq("auto-generated")))
                .thenReturn(false);

        assertDoesNotThrow(() -> discoveryService.discoverAndRegenerate(tenantId, credentials));

        verify(droolsRuleService).rollbackToLastValid(tenantId);
    }

    @Test
    void scheduledDiscovery_whenDisabled_logsAndReturns() throws Exception {
        setDiscoveryEnabled(false);
        assertDoesNotThrow(() -> discoveryService.scheduledDiscovery());
        verify(credentialRepository, never()).findAll();
    }

    @Test
    void scheduledDiscovery_withNoCredentials_logsAndReturns() throws Exception {
        setDiscoveryEnabled(true);
        when(credentialRepository.findAll()).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> discoveryService.scheduledDiscovery());

        verify(credentialRepository).findAll();
    }

    @Test
    void scheduledDiscovery_withActiveCredentials_processesTenants() throws Exception {
        setDiscoveryEnabled(true);
        Credential cred1 = new Credential();
        cred1.setId(UUID.randomUUID());
        cred1.setTenantId("tenant1");
        cred1.setKeyId("AKIA1234567890");
        cred1.setKeySecret("secret123");
        cred1.setStatus(Credential.CredentialStatus.ACTIVE);

        Credential cred2 = new Credential();
        cred2.setId(UUID.randomUUID());
        cred2.setTenantId("tenant1");
        cred2.setKeyId("AKIA0987654321");
        cred2.setKeySecret("secret456");
        cred2.setStatus(Credential.CredentialStatus.INACTIVE);

        when(credentialRepository.findAll()).thenReturn(Arrays.asList(cred1, cred2));
        when(droolsRuleGenerator.generate(anyString(), any(DiscoveredPermissions.class)))
                .thenReturn("package com.test;\nrule \"test\"\n  salience 40\nwhen\nthen\nend\n");
        when(clientRuleRepository.findActiveByTenantId("tenant1")).thenReturn(Optional.empty());
        when(semaphoreService.acquireSemaphore("rule_generation_tenant1", 15 * 60, 30))
                .thenReturn(true);
        when(droolsRuleService.updateRulesForTenant(anyString(), any(byte[].class), anyString()))
                .thenReturn(true);

        assertDoesNotThrow(() -> discoveryService.scheduledDiscovery());

        verify(droolsRuleGenerator).generate(anyString(), any(DiscoveredPermissions.class));
        verify(droolsRuleService).updateRulesForTenant(anyString(), any(byte[].class), anyString());
    }

    @Test
    void scheduledDiscovery_skipsWhenNoChanges() throws Exception {
        setDiscoveryEnabled(true);
        Credential cred = new Credential();
        cred.setId(UUID.randomUUID());
        cred.setTenantId("tenant1");
        cred.setKeyId("AKIA1234567890");
        cred.setKeySecret("secret123");
        cred.setStatus(Credential.CredentialStatus.ACTIVE);

        when(credentialRepository.findAll()).thenReturn(Arrays.asList(cred));

        String drl = "package com.test;\nrule \"test\"\n  salience 40\nwhen\nthen\nend\n";
        when(droolsRuleGenerator.generate(eq("tenant1"), any(DiscoveredPermissions.class)))
                .thenReturn(drl);

        ClientRule existingRule = new ClientRule();
        existingRule.setTenantId("tenant1");
        existingRule.setVersion(1);
        existingRule.setDrlContent(drl.getBytes());
        when(clientRuleRepository.findActiveByTenantId("tenant1")).thenReturn(Optional.of(existingRule));

        assertDoesNotThrow(() -> discoveryService.scheduledDiscovery());

        verify(droolsRuleService, never()).updateRulesForTenant(anyString(), any(byte[].class), anyString());
    }

    @Test
    void scheduledDiscovery_handlesException_continuesWithOtherTenants() throws Exception {
        setDiscoveryEnabled(true);
        Credential cred1 = new Credential();
        cred1.setId(UUID.randomUUID());
        cred1.setTenantId("tenant1");
        cred1.setKeyId("AKIA1234567890");
        cred1.setKeySecret("secret123");
        cred1.setStatus(Credential.CredentialStatus.ACTIVE);

        Credential cred2 = new Credential();
        cred2.setId(UUID.randomUUID());
        cred2.setTenantId("tenant2");
        cred2.setKeyId("AKIA0987654321");
        cred2.setKeySecret("secret456");
        cred2.setStatus(Credential.CredentialStatus.ACTIVE);

        when(credentialRepository.findAll()).thenReturn(Arrays.asList(cred1, cred2));
        doThrow(new AwsMetadataDiscoveryService.CredentialExpiredException("PENDING: CRED_REFRESH", null))
                .when(droolsRuleGenerator).generate(eq("tenant1"), any(DiscoveredPermissions.class));

        when(droolsRuleGenerator.generate(eq("tenant2"), any(DiscoveredPermissions.class)))
                .thenReturn("package com.test;\nrule \"test\"\n  salience 40\nwhen\nthen\nend\n");
        when(clientRuleRepository.findActiveByTenantId("tenant2")).thenReturn(Optional.empty());
        when(semaphoreService.acquireSemaphore("rule_generation_tenant2", 15 * 60, 30))
                .thenReturn(true);
        when(droolsRuleService.updateRulesForTenant(eq("tenant2"), any(byte[].class), eq("auto-generated")))
                .thenReturn(true);

        assertDoesNotThrow(() -> discoveryService.scheduledDiscovery());

        verify(droolsRuleService).updateRulesForTenant(eq("tenant2"), any(byte[].class), eq("auto-generated"));
    }

    @Test
    void pushDiscovery_normalFlow_returnsUpdatedResult() {
        String tenantId = "tenant1";
        Map<String, String> awsCredentials = new HashMap<>();
        awsCredentials.put("accessKey", "AKIA1234567890");
        awsCredentials.put("secretKey", "secret123");

        DiscoveredPermissions permissions = new DiscoveredPermissions(
                tenantId, true, false, false, false, false, false, false
        );
        String drl = "package com.test;\nrule \"test\"\n  salience 40\nwhen\nthen\nend\n";

        when(droolsRuleGenerator.generate(eq(tenantId), any(DiscoveredPermissions.class)))
                .thenReturn(drl);
        when(clientRuleRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.empty());
        when(semaphoreService.acquireSemaphore("rule_generation_tenant1", 15 * 60, 30))
                .thenReturn(true);
        when(droolsRuleService.validateDrl(any(byte[].class))).thenReturn(0);
        when(droolsRuleService.getMaxDrlSizeBytes()).thenReturn(51200);
        when(clientRuleRepository.findMaxVersionByTenantId(tenantId)).thenReturn(null);
        when(clientRuleRepository.save(any(ClientRule.class))).thenAnswer(invocation -> {
            ClientRule rule = invocation.getArgument(0);
            rule.setId(UUID.randomUUID());
            rule.setCreatedAt(Instant.now());
            return rule;
        });

        AwsMetadataDiscoveryService.PushDiscoveryResult result =
                discoveryService.pushDiscovery(tenantId, awsCredentials, "test-source");

        assertEquals(AwsMetadataDiscoveryService.PushDiscoveryResult.Status.UPDATED, result.getStatus());
        assertEquals(tenantId, result.getTenantId());
        assertEquals(1, result.getVersion().intValue());
        assertNotNull(result.getHash());
        assertEquals("test-source", result.getSource());

        verify(clientRuleRepository).save(any(ClientRule.class));
        verify(droolsRuleService).invalidateCache(tenantId);
    }

    @Test
    void pushDiscovery_semaphoreHeld_returnsSkipped() {
        String tenantId = "tenant1";
        Map<String, String> awsCredentials = new HashMap<>();
        awsCredentials.put("accessKey", "AKIA1234567890");
        awsCredentials.put("secretKey", "secret123");

        when(semaphoreService.acquireSemaphore("rule_generation_tenant1", 15 * 60, 30))
                .thenReturn(false);

        AwsMetadataDiscoveryService.PushDiscoveryResult result =
                discoveryService.pushDiscovery(tenantId, awsCredentials, "test-source");

        assertEquals(AwsMetadataDiscoveryService.PushDiscoveryResult.Status.SKIPPED, result.getStatus());
        assertEquals(tenantId, result.getTenantId());

        verify(droolsRuleGenerator, never()).generate(anyString(), any(DiscoveredPermissions.class));
    }

    @Test
    void pushDiscovery_noChanges_returnsNoChanges() {
        String tenantId = "tenant1";
        Map<String, String> awsCredentials = new HashMap<>();
        awsCredentials.put("accessKey", "AKIA1234567890");
        awsCredentials.put("secretKey", "secret123");

        String drl = "package com.test;\nrule \"test\"\n  salience 40\nwhen\nthen\nend\n";
        when(droolsRuleGenerator.generate(eq(tenantId), any(DiscoveredPermissions.class)))
                .thenReturn(drl);

        ClientRule existingRule = new ClientRule();
        existingRule.setTenantId(tenantId);
        existingRule.setVersion(1);
        existingRule.setDrlContent(drl.getBytes());
        when(clientRuleRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.of(existingRule));
        when(semaphoreService.acquireSemaphore("rule_generation_tenant1", 15 * 60, 30))
                .thenReturn(true);

        AwsMetadataDiscoveryService.PushDiscoveryResult result =
                discoveryService.pushDiscovery(tenantId, awsCredentials, "test-source");

        assertEquals(AwsMetadataDiscoveryService.PushDiscoveryResult.Status.NO_CHANGES, result.getStatus());
        assertEquals(tenantId, result.getTenantId());
        assertNotNull(result.getHash());
    }

    @Test
    void pushDiscovery_validationFailed_returnsValidationError() {
        String tenantId = "tenant1";
        Map<String, String> awsCredentials = new HashMap<>();
        awsCredentials.put("accessKey", "AKIA1234567890");
        awsCredentials.put("secretKey", "secret123");

        String drl = "package com.test;\nrule \"test\"\n  salience 40\nwhen\nthen\nend\n";
        when(droolsRuleGenerator.generate(eq(tenantId), any(DiscoveredPermissions.class)))
                .thenReturn(drl);
        when(clientRuleRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.empty());
        when(semaphoreService.acquireSemaphore("rule_generation_tenant1", 15 * 60, 30))
                .thenReturn(true);
        when(droolsRuleService.validateDrl(any(byte[].class))).thenReturn(3);

        AwsMetadataDiscoveryService.PushDiscoveryResult result =
                discoveryService.pushDiscovery(tenantId, awsCredentials, "test-source");

        assertEquals(AwsMetadataDiscoveryService.PushDiscoveryResult.Status.VALIDATION_FAILED, result.getStatus());
        assertEquals(tenantId, result.getTenantId());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void pushDiscovery_exceedsMaxSize_returnsValidationError() {
        String tenantId = "tenant1";
        Map<String, String> awsCredentials = new HashMap<>();
        awsCredentials.put("accessKey", "AKIA1234567890");
        awsCredentials.put("secretKey", "secret123");

        String largeDrl = "package com.test;\n";
        for (int i = 0; i < 2000; i++) {
            largeDrl += "rule \"rule_" + i + "\"\n  salience 40\nwhen\nthen\nend\n";
        }

        when(droolsRuleGenerator.generate(eq(tenantId), any(DiscoveredPermissions.class)))
                .thenReturn(largeDrl);
        when(clientRuleRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.empty());
        when(semaphoreService.acquireSemaphore("rule_generation_tenant1", 15 * 60, 30))
                .thenReturn(true);
        when(droolsRuleService.validateDrl(any(byte[].class))).thenReturn(0);
        when(droolsRuleService.getMaxDrlSizeBytes()).thenReturn(100);

        AwsMetadataDiscoveryService.PushDiscoveryResult result =
                discoveryService.pushDiscovery(tenantId, awsCredentials, "test-source");

        assertEquals(AwsMetadataDiscoveryService.PushDiscoveryResult.Status.VALIDATION_FAILED, result.getStatus());
        assertEquals(tenantId, result.getTenantId());
    }

    @Test
    void pushDiscovery_drlContentUnchanged_returnsNoChanges() {
        String tenantId = "tenant1";
        Map<String, String> awsCredentials = new HashMap<>();
        awsCredentials.put("accessKey", "AKIA1234567890");
        awsCredentials.put("secretKey", "secret123");

        String drl = "package com.test;\nrule \"test\"\n  salience 40\nwhen\nthen\nend\n";
        when(droolsRuleGenerator.generate(eq(tenantId), any(DiscoveredPermissions.class)))
                .thenReturn(drl);

        ClientRule existingRule = new ClientRule();
        existingRule.setTenantId(tenantId);
        existingRule.setVersion(1);
        existingRule.setDrlContent(drl.getBytes());
        when(clientRuleRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.of(existingRule));
        when(semaphoreService.acquireSemaphore("rule_generation_tenant1", 15 * 60, 30))
                .thenReturn(true);

        AwsMetadataDiscoveryService.PushDiscoveryResult result =
                discoveryService.pushDiscovery(tenantId, awsCredentials, "test-source");

        assertEquals(AwsMetadataDiscoveryService.PushDiscoveryResult.Status.NO_CHANGES, result.getStatus());
        verify(clientRuleRepository, never()).save(any(ClientRule.class));
        verify(droolsRuleService, never()).invalidateCache(anyString());
    }

    @Test
    void pushDiscovery_withExistingVersionIncrementsVersion() {
        String tenantId = "tenant1";
        Map<String, String> awsCredentials = new HashMap<>();
        awsCredentials.put("accessKey", "AKIA1234567890");
        awsCredentials.put("secretKey", "secret123");

        String drl = "package com.test;\nrule \"test\"\n  salience 40\nwhen\nthen\nend\n";
        when(droolsRuleGenerator.generate(eq(tenantId), any(DiscoveredPermissions.class)))
                .thenReturn(drl);
        when(clientRuleRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.empty());
        when(semaphoreService.acquireSemaphore("rule_generation_tenant1", 15 * 60, 30))
                .thenReturn(true);
        when(droolsRuleService.validateDrl(any(byte[].class))).thenReturn(0);
        when(droolsRuleService.getMaxDrlSizeBytes()).thenReturn(51200);
        when(clientRuleRepository.findMaxVersionByTenantId(tenantId)).thenReturn(5);
        when(clientRuleRepository.save(any(ClientRule.class))).thenAnswer(invocation -> {
            ClientRule rule = invocation.getArgument(0);
            rule.setId(UUID.randomUUID());
            rule.setCreatedAt(Instant.now());
            return rule;
        });

        AwsMetadataDiscoveryService.PushDiscoveryResult result =
                discoveryService.pushDiscovery(tenantId, awsCredentials, "test-source");

        assertEquals(AwsMetadataDiscoveryService.PushDiscoveryResult.Status.UPDATED, result.getStatus());
        assertEquals(6, result.getVersion().intValue());
    }

    @Test
    void credentialExpiredException_getState() {
        AwsMetadataDiscoveryService.CredentialExpiredException ex =
                new AwsMetadataDiscoveryService.CredentialExpiredException("PENDING: CRED_REFRESH", null);

        assertEquals("PENDING: CRED_REFRESH", ex.getState());
        assertNull(ex.getCause());
    }

    @Test
    void pushDiscoveryResult_allStatuses() {
        AwsMetadataDiscoveryService.PushDiscoveryResult updated =
                AwsMetadataDiscoveryService.PushDiscoveryResult.updated("t1", 1, "hash123", "webhook");
        assertEquals(AwsMetadataDiscoveryService.PushDiscoveryResult.Status.UPDATED, updated.getStatus());
        assertEquals("t1", updated.getTenantId());
        assertEquals(1, updated.getVersion().intValue());
        assertEquals("hash123", updated.getHash());
        assertEquals("webhook", updated.getSource());

        AwsMetadataDiscoveryService.PushDiscoveryResult noChanges =
                AwsMetadataDiscoveryService.PushDiscoveryResult.noChanges("t1", "hash123");
        assertEquals(AwsMetadataDiscoveryService.PushDiscoveryResult.Status.NO_CHANGES, noChanges.getStatus());
        assertNull(noChanges.getVersion());
        assertEquals("hash123", noChanges.getHash());

        AwsMetadataDiscoveryService.PushDiscoveryResult skipped =
                AwsMetadataDiscoveryService.PushDiscoveryResult.skipped("t1");
        assertEquals(AwsMetadataDiscoveryService.PushDiscoveryResult.Status.SKIPPED, skipped.getStatus());

        AwsMetadataDiscoveryService.PushDiscoveryResult expired =
                AwsMetadataDiscoveryService.PushDiscoveryResult.credentialExpired("t1", "AccessDenied");
        assertEquals(AwsMetadataDiscoveryService.PushDiscoveryResult.Status.CREDENTIAL_EXPIRED, expired.getStatus());
        assertNotNull(expired.getErrorMessage());

        AwsMetadataDiscoveryService.PushDiscoveryResult failed =
                AwsMetadataDiscoveryService.PushDiscoveryResult.failed("t1", "AWS error");
        assertEquals(AwsMetadataDiscoveryService.PushDiscoveryResult.Status.FAILED, failed.getStatus());
        assertEquals("AWS error", failed.getErrorMessage());

        AwsMetadataDiscoveryService.PushDiscoveryResult validationFailed =
                AwsMetadataDiscoveryService.PushDiscoveryResult.validationFailed("t1", 2);
        assertEquals(AwsMetadataDiscoveryService.PushDiscoveryResult.Status.VALIDATION_FAILED, validationFailed.getStatus());
        assertNotNull(validationFailed.getErrorMessage());
    }
}
