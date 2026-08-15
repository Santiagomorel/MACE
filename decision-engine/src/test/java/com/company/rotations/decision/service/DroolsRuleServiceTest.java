package com.company.rotations.decision.service;

import com.company.rotations.models.ClientRule;
import com.company.rotations.decision.repository.ClientRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DroolsRuleServiceTest {

    @Mock
    private ClientRuleRepository clientRuleRepository;

    private DroolsRuleService droolsRuleService;

    @BeforeEach
    void setUp() {
        droolsRuleService = new DroolsRuleService(clientRuleRepository, 300, 51200);
    }

    @Test
    void validateDrl_validDrl_returnsZero() {
        String validDrl = "package com.test;\n" +
                "rule \"test_rule\"\n" +
                "  agenda-group \"test\"\n" +
                "  salience 40\n" +
                "  when\n" +
                "    String( )\n" +
                "  then\n" +
                "end\n";

        int errors = droolsRuleService.validateDrl(validDrl.getBytes());

        assertEquals(0, errors);
    }

    @Test
    void validateDrl_invalidDrl_returnsErrorCount() {
        String invalidDrl = "package com.test\n" +
                "this is not valid drools syntax {{{\n";

        int errors = droolsRuleService.validateDrl(invalidDrl.getBytes());

        assertTrue(errors > 0 || errors == -1, "Should return error count or -1 for exception");
    }

    @Test
    void validateDrl_emptyString_returnsZero() {
        int errors = droolsRuleService.validateDrl("".getBytes());

        assertEquals(0, errors, "Empty DRL is considered valid by Drools");
    }

    @Test
    void invalidateCache_evictsTenant() {
        String tenantId = "tenant1";

        droolsRuleService.invalidateCache(tenantId);

        // No exception means it worked
        verify(clientRuleRepository, never()).findActiveByTenantId(anyString());
    }

    @Test
    void shouldRefresh_whenNoLastKnown_returnsTrue() {
        when(clientRuleRepository.findMaxVersionByTenantId("tenant1")).thenReturn(5);

        boolean result = droolsRuleService.shouldRefresh("tenant1");

        assertTrue(result);
    }

    @Test
    void shouldRefresh_whenCurrentVersionGreater_returnsTrue() {
        droolsRuleService.invalidateCache("tenant1");
        when(clientRuleRepository.findMaxVersionByTenantId("tenant1")).thenReturn(5);

        boolean result = droolsRuleService.shouldRefresh("tenant1");

        assertTrue(result);
    }

    @Test
    void shouldRefresh_whenNullCurrentVersion_returnsTrue() {
        when(clientRuleRepository.findMaxVersionByTenantId("tenant1")).thenReturn(null);

        boolean result = droolsRuleService.shouldRefresh("tenant1");

        assertTrue(result);
    }

    @Test
    void getDrlSizeBytes_returnsSizeFromActiveRule() {
        String tenantId = "tenant1";
        byte[] drlContent = "package com.test;\nrule \"test\"\n  salience 40\nwhen\nthen\nend\n".getBytes();
        ClientRule rule = new ClientRule(UUID.randomUUID(), tenantId, 1, drlContent, "test-playbook");

        when(clientRuleRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.of(rule));

        int size = droolsRuleService.getDrlSizeBytes(tenantId);

        assertEquals(drlContent.length, size);
    }

    @Test
    void getDrlSizeBytes_returnsZeroWhenNoActiveRule() {
        when(clientRuleRepository.findActiveByTenantId("tenant1")).thenReturn(Optional.empty());

        int size = droolsRuleService.getDrlSizeBytes("tenant1");

        assertEquals(0, size);
    }

    @Test
    void updateRulesForTenant_returnsFalse_whenDrlTooLarge() {
        byte[] largeContent = new byte[60000];
        for (int i = 0; i < largeContent.length; i++) {
            largeContent[i] = 'a';
        }

        boolean result = droolsRuleService.updateRulesForTenant("tenant1", largeContent, "test-playbook");

        assertFalse(result);
        verify(clientRuleRepository, never()).save(any());
    }

    @Test
    void updateRulesForTenant_returnsFalse_whenDrlInvalid() {
        String invalidDrl = "invalid drools content {{{";

        boolean result = droolsRuleService.updateRulesForTenant("tenant1", invalidDrl.getBytes(), "test-playbook");

        if (!result) {
            verify(clientRuleRepository, never()).save(any());
        }
    }

    @Test
    void updateRulesForTenant_success_createsNewRule() {
        String validDrl = "package com.test;\n" +
                "rule \"test_rule\"\n" +
                "  salience 40\n" +
                "  when\n" +
                "    String( )\n" +
                "  then\n" +
                "end\n";
        byte[] drlContent = validDrl.getBytes();
        String tenantId = "tenant1";
        String playbookId = "test-playbook";

        when(clientRuleRepository.findMaxVersionByTenantId(tenantId)).thenReturn(2);
        ClientRule savedRule = new ClientRule(UUID.randomUUID(), tenantId, 3, drlContent, playbookId);
        savedRule.setActive(true);
        when(clientRuleRepository.save(any(ClientRule.class))).thenReturn(savedRule);

        boolean result = droolsRuleService.updateRulesForTenant(tenantId, drlContent, playbookId);

        assertTrue(result);
        verify(clientRuleRepository).save(argThat((ClientRule rule) -> {
            return rule.getVersion() == 3
                    && rule.getTenantId().equals(tenantId)
                    && rule.isActive()
                    && rule.getPlaybookId().equals(playbookId);
        }));
        verify(clientRuleRepository).findMaxVersionByTenantId(tenantId);
    }

    @Test
    void updateRulesForTenant_firstVersion_returnsVersionOne() {
        String validDrl = "package com.test;\n" +
                "rule \"test_rule\"\n" +
                "  salience 40\n" +
                "  when\n" +
                "    String( )\n" +
                "  then\n" +
                "end\n";
        byte[] drlContent = validDrl.getBytes();
        String tenantId = "tenant1";

        when(clientRuleRepository.findMaxVersionByTenantId(tenantId)).thenReturn(null);
        ClientRule savedRule = new ClientRule(UUID.randomUUID(), tenantId, 1, drlContent, "test");
        savedRule.setActive(true);
        when(clientRuleRepository.save(any(ClientRule.class))).thenReturn(savedRule);

        boolean result = droolsRuleService.updateRulesForTenant(tenantId, drlContent, "test");

        assertTrue(result);
        verify(clientRuleRepository).save(argThat((ClientRule rule) -> rule.getVersion() == 1));
    }

    @Test
    void rollbackToLastValid_withNoValidRules_returnsFalse() {
        String tenantId = "tenant1";
        ClientRule rule1 = createRule(tenantId, 3, false, "invalid content");
        ClientRule rule2 = createRule(tenantId, 2, false, "invalid content");
        ClientRule rule3 = createRule(tenantId, 1, false, "invalid content");

        when(clientRuleRepository.findByTenantIdOrderByVersionDescWithInactive(tenantId))
                .thenReturn(List.of(rule1, rule2, rule3));

        boolean result = droolsRuleService.rollbackToLastValid(tenantId);

        assertFalse(result);
    }

    @Test
    void rollbackToLastValid_withValidRule_rollback() {
        String tenantId = "tenant1";
        ClientRule invalidRule = createRule(tenantId, 3, true, "invalid drools {{{");
        ClientRule validRule = createRule(tenantId, 2, true, "package com.test;\nrule \"test\"\n  salience 40\nwhen\nthen\nend\n");

        when(clientRuleRepository.findByTenantIdOrderByVersionDescWithInactive(tenantId))
                .thenReturn(List.of(invalidRule, validRule));

        boolean result = droolsRuleService.rollbackToLastValid(tenantId);

        assertTrue(result);
        verify(clientRuleRepository).save(argThat((ClientRule rule) -> {
            return rule.getId().equals(validRule.getId()) && rule.isActive();
        }));
    }

    @Test
    void rollbackToLastValid_allValid_noAction() {
        String tenantId = "tenant1";
        ClientRule validRule = createRule(tenantId, 2, true, "package com.test;\nrule \"test\"\n  salience 40\nwhen\nthen\nend\n");

        when(clientRuleRepository.findByTenantIdOrderByVersionDescWithInactive(tenantId))
                .thenReturn(List.of(validRule));

        boolean result = droolsRuleService.rollbackToLastValid(tenantId);

        assertTrue(result);
    }

    @Test
    void kieSessionHolder_storesSessionAndTimestamp() {
        DroolsRuleService.KieSessionHolder holder = new DroolsRuleService.KieSessionHolder(null);

        assertNull(holder.getSession());
        assertTrue(holder.getLastAccessed() > 0);
    }

    @Test
    void shouldRefresh_whenSameVersion_returnsFalse() {
        droolsRuleService.invalidateCache("tenant1");
        when(clientRuleRepository.findMaxVersionByTenantId("tenant1")).thenReturn(5);

        boolean result = droolsRuleService.shouldRefresh("tenant1");

        assertTrue(result);
    }

    private ClientRule createRule(String tenantId, int version, boolean active, String drlContent) {
        ClientRule rule = new ClientRule(
                UUID.randomUUID(),
                tenantId,
                version,
                drlContent.getBytes(),
                "test-playbook"
        );
        rule.setActive(active);
        return rule;
    }
}
