package com.company.rotations.decision.controller;

import com.company.rotations.decision.domain.Playbook;
import com.company.rotations.decision.service.AwsMetadataDiscoveryService;
import com.company.rotations.decision.service.CriticalityCalculator;
import com.company.rotations.decision.service.DroolsRuleService;
import com.company.rotations.decision.service.PlaybookLoaderService;
import com.company.rotations.models.ClientRule;
import com.company.rotations.decision.repository.ClientRuleRepository;
import com.company.rotations.models.Severidad;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DecisionEngineController.class)
class DecisionEngineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CriticalityCalculator criticalityCalculator;

    @MockBean
    private PlaybookLoaderService playbookLoaderService;

    @MockBean
    private DroolsRuleService droolsRuleService;

    @MockBean
    private ClientRuleRepository clientRuleRepository;

    @MockBean
    private AwsMetadataDiscoveryService discoveryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Playbook createTestPlaybook() {
        Playbook playbook = new Playbook();
        playbook.setPlaybookId("aws-access-key-exposed");
        playbook.setVersion("1.0.0");
        playbook.setCredentialTypes(List.of("AKIA"));
        Map<String, Severidad> floor = new LinkedHashMap<>();
        floor.put("s3_full_access", Severidad.CRITICO);
        floor.put("s3_read_only", Severidad.ALTO);
        playbook.setSeverityFloor(floor);
        Playbook.AutoRotate autoRotate = new Playbook.AutoRotate();
        autoRotate.setEnabled(true);
        autoRotate.setMaxWindowMins(15);
        playbook.setAutoRotate(autoRotate);
        playbook.setCanLowerFloor(false);
        return playbook;
    }

    @Test
    void getRules_whenActiveRuleExists_returnsRuleDetails() throws Exception {
        String tenantId = "tenant1";
        byte[] drlContent = "package com.test;\nrule \"test\"\n  salience 40\nwhen\nthen\nend\n".getBytes();
        ClientRule rule = new ClientRule(
                UUID.randomUUID(), tenantId, 3, drlContent, "aws-access-key-exposed"
        );
        rule.setActive(true);
        rule.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));

        when(clientRuleRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.of(rule));

        mockMvc.perform(get("/api/" + tenantId + "/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenantId))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.drlSizeBytes").value(drlContent.length))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.manualOverride").value(false));

        verify(clientRuleRepository).findActiveByTenantId(tenantId);
    }

    @Test
    void getRules_whenNoActiveRule_returnsEmptyList() throws Exception {
        String tenantId = "tenant1";

        when(clientRuleRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/" + tenantId + "/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenantId))
                .andExpect(jsonPath("$.rules").isArray())
                .andExpect(jsonPath("$.rules").isArray())
                .andExpect(jsonPath("$.message").value("No rules found for tenant"));
    }

    @Test
    void updateRules_withValidSeverity_returnsApplied() throws Exception {
        String tenantId = "tenant1";
        Map<String, Object> request = Map.of("severity", "ALTO");

        byte[] drlContent = "package com.test;\nrule \"test\"\n  salience 40\nwhen\nthen\nend\n".getBytes();
        ClientRule rule = new ClientRule(UUID.randomUUID(), tenantId, 1, drlContent, "aws-access-key-exposed");
        rule.setActive(true);
        when(clientRuleRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.of(rule));
        when(playbookLoaderService.loadPlaybookById("aws-access-key-exposed")).thenReturn(createTestPlaybook());

        mockMvc.perform(post("/api/" + tenantId + "/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.severity").value("ALTO"))
                .andExpect(jsonPath("$.manualOverride").value(true))
                .andExpect(jsonPath("$.user").value("system"));
    }

    @Test
    void updateRules_withSeverityLowerThanFloor_returnsBadRequest() throws Exception {
        String tenantId = "tenant1";
        Map<String, Object> request = Map.of("severity", "BAJO");

        byte[] drlContent = "package com.test;\nrule \"test\"\n  salience 40\nwhen\nthen\nend\n".getBytes();
        ClientRule rule = new ClientRule(UUID.randomUUID(), tenantId, 1, drlContent, "aws-access-key-exposed");
        rule.setActive(true);
        when(clientRuleRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.of(rule));
        when(playbookLoaderService.loadPlaybookById("aws-access-key-exposed")).thenReturn(createTestPlaybook());

        mockMvc.perform(post("/api/" + tenantId + "/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cannot lower below playbook floor: ALTO"));
    }

    @Test
    void updateRules_withInvalidSeverity_returnsBadRequest() throws Exception {
        String tenantId = "tenant1";
        Map<String, Object> request = Map.of("severity", "INVALID_SEVERITY");

        mockMvc.perform(post("/api/" + tenantId + "/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid severity: INVALID_SEVERITY"));
    }

    @Test
    void updateRules_withoutSeverity_returnsResponse() throws Exception {
        String tenantId = "tenant1";
        Map<String, Object> request = Map.of("user", "admin");

        mockMvc.perform(post("/api/" + tenantId + "/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").doesNotExist());
    }

    @Test
    void updateRules_withManualOverrideAndUser() throws Exception {
        String tenantId = "tenant1";
        Map<String, Object> request = Map.of("severity", "ALTO", "manualOverride", true, "user", "admin");

        byte[] drlContent = "package com.test;\nrule \"test\"\n  salience 40\nwhen\nthen\nend\n".getBytes();
        ClientRule rule = new ClientRule(UUID.randomUUID(), tenantId, 1, drlContent, "aws-access-key-exposed");
        rule.setActive(true);
        when(clientRuleRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.of(rule));
        when(playbookLoaderService.loadPlaybookById("aws-access-key-exposed")).thenReturn(createTestPlaybook());

        mockMvc.perform(post("/api/" + tenantId + "/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value("admin"));
    }

    @Test
    void discoverAndRegenerate_success() throws Exception {
        String tenantId = "tenant1";
        Map<String, String> credentials = Map.of("accessKey", "AKIA123", "secretKey", "secret");

        doNothing().when(discoveryService).discoverAndRegenerate(eq(tenantId), anyMap());

        mockMvc.perform(post("/api/" + tenantId + "/rules/discover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(credentials)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.tenantId").value(tenantId));
    }

    @Test
    void discoverAndRegenerate_credentialExpired() throws Exception {
        String tenantId = "tenant1";
        Map<String, String> credentials = Map.of("accessKey", "AKIA123", "secretKey", "secret");

        doThrow(new AwsMetadataDiscoveryService.CredentialExpiredException("PENDING: CRED_REFRESH", null))
                .when(discoveryService).discoverAndRegenerate(eq(tenantId), anyMap());

        mockMvc.perform(post("/api/" + tenantId + "/rules/discover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(credentials)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("credential_expired"))
                .andExpect(jsonPath("$.state").value("PENDING: CRED_REFRESH"))
                .andExpect(jsonPath("$.message").value("AWS credentials have expired. Please update credentials."));
    }

    @Test
    void discoverAndRegenerate_genericException() throws Exception {
        String tenantId = "tenant1";
        Map<String, String> credentials = Map.of("accessKey", "AKIA123", "secretKey", "secret");

        doThrow(new RuntimeException("AWS error")).when(discoveryService).discoverAndRegenerate(eq(tenantId), anyMap());

        mockMvc.perform(post("/api/" + tenantId + "/rules/discover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(credentials)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("AWS error"));
    }

    @Test
    void getAllPlaybooks_returnsAllPlaybooks() throws Exception {
        when(playbookLoaderService.loadPlaybookById("aws-access-key-exposed")).thenReturn(createTestPlaybook());
        when(playbookLoaderService.loadPlaybookById("aws-session-token-leaked")).thenReturn(createSessionTokenPlaybook());
        when(playbookLoaderService.loadPlaybookById("aws-root-credentials-exposed")).thenReturn(createRootCredentialsPlaybook());
        when(playbookLoaderService.loadPlaybookById("aws-iam-role-assumption-abuse")).thenReturn(createIamRolePlaybook());

        mockMvc.perform(get("/api/playbooks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].playbookId").value("aws-access-key-exposed"))
                .andExpect(jsonPath("$[1].playbookId").value("aws-session-token-leaked"))
                .andExpect(jsonPath("$[2].playbookId").value("aws-root-credentials-exposed"))
                .andExpect(jsonPath("$[3].playbookId").value("aws-iam-role-assumption-abuse"));
    }

    @Test
    void getAllPlaybooks_withNullPlaybooks_returnsEmptyList() throws Exception {
        when(playbookLoaderService.loadPlaybookById(anyString())).thenReturn(null);

        mockMvc.perform(get("/api/playbooks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void validateDrl_validDrl_returnsValid() throws Exception {
        Map<String, String> request = Map.of("drl", "package com.test;\nrule \"test\"\n  salience 40\nwhen\nthen\nend\n");

        mockMvc.perform(post("/api/validate-drl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.errors").value(0));
    }

    @Test
    void validateDrl_invalidDrl_returnsInvalid() throws Exception {
        Map<String, String> request = Map.of("drl", "invalid drools {{{");

        when(droolsRuleService.validateDrl(any(byte[].class))).thenReturn(1);

        mockMvc.perform(post("/api/validate-drl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));

        verify(droolsRuleService).validateDrl(any(byte[].class));
    }

    @Test
    void validateDrl_emptyDrl_returnsBadRequest() throws Exception {
        Map<String, String> request = Map.of("drl", "");

        mockMvc.perform(post("/api/validate-drl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.error").value("DRL content is required"));
    }

    @Test
    void validateDrl_nullDrl_returnsBadRequest() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("drl", null);

        mockMvc.perform(post("/api/validate-drl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validateDrl_missingDrlField_returnsBadRequest() throws Exception {
        Map<String, String> request = new HashMap<>();

        mockMvc.perform(post("/api/validate-drl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRules_canLowerFloorAllowsLower() throws Exception {
        String tenantId = "tenant1";
        Map<String, Object> request = Map.of("severity", "BAJO");

        byte[] drlContent = "package com.test;\nrule \"test\"\n  salience 40\nwhen\nthen\nend\n".getBytes();
        ClientRule rule = new ClientRule(UUID.randomUUID(), tenantId, 1, drlContent, "test");
        rule.setActive(true);
        when(clientRuleRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.of(rule));

        Playbook playbook = createTestPlaybook();
        playbook.setCanLowerFloor(true);
        when(playbookLoaderService.loadPlaybookById("aws-access-key-exposed")).thenReturn(playbook);

        mockMvc.perform(post("/api/" + tenantId + "/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true));
    }

    private Playbook createSessionTokenPlaybook() {
        Playbook playbook = new Playbook();
        playbook.setPlaybookId("aws-session-token-leaked");
        playbook.setVersion("1.0.0");
        playbook.setCredentialTypes(List.of("ASIA"));
        Map<String, Severidad> floor = new LinkedHashMap<>();
        floor.put("assumed_role_read_only", Severidad.MEDIA);
        playbook.setSeverityFloor(floor);
        Playbook.AutoRotate autoRotate = new Playbook.AutoRotate();
        autoRotate.setEnabled(false);
        autoRotate.setMaxWindowMins(null);
        playbook.setAutoRotate(autoRotate);
        playbook.setCanLowerFloor(false);
        return playbook;
    }

    private Playbook createRootCredentialsPlaybook() {
        Playbook playbook = new Playbook();
        playbook.setPlaybookId("aws-root-credentials-exposed");
        playbook.setVersion("1.0.0");
        playbook.setCredentialTypes(List.of("ROOT_"));
        Map<String, Severidad> floor = new LinkedHashMap<>();
        floor.put("ec2_active", Severidad.CRITICO);
        playbook.setSeverityFloor(floor);
        Playbook.AutoRotate autoRotate = new Playbook.AutoRotate();
        autoRotate.setEnabled(true);
        autoRotate.setMaxWindowMins(15);
        playbook.setAutoRotate(autoRotate);
        playbook.setCanLowerFloor(false);
        return playbook;
    }

    private Playbook createIamRolePlaybook() {
        Playbook playbook = new Playbook();
        playbook.setPlaybookId("aws-iam-role-assumption-abuse");
        playbook.setVersion("1.0.0");
        playbook.setCredentialTypes(List.of("AKIA", "ASIA"));
        Map<String, Severidad> floor = new LinkedHashMap<>();
        floor.put("regular_role_from_internal_source_verified", Severidad.MEDIA);
        playbook.setSeverityFloor(floor);
        Playbook.AutoRotate autoRotate = new Playbook.AutoRotate();
        autoRotate.setEnabled(false);
        autoRotate.setMaxWindowMins(null);
        playbook.setAutoRotate(autoRotate);
        playbook.setCanLowerFloor(false);
        return playbook;
    }
}
