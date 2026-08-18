package com.company.rotations.decision.service;

import com.company.rotations.decision.domain.Playbook;
import com.company.rotations.models.PlaybookStandard;
import com.company.rotations.models.Severidad;
import com.company.rotations.decision.repository.PlaybookRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PlaybookLoaderService {

    private static final Logger log = LoggerFactory.getLogger(PlaybookLoaderService.class);
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().build();

    private final PlaybookRepository playbookRepository;
    private final Map<String, Playbook> playbookCache = new ConcurrentHashMap<>();

    public PlaybookLoaderService(PlaybookRepository playbookRepository) {
        this.playbookRepository = playbookRepository;
    }

    public Playbook loadPlaybook(String credentialType) {
        List<String> playbookIds = resolvePlaybookIdsByCredentialType(credentialType);
        for (String id : playbookIds) {
            Playbook playbook = loadPlaybookById(id);
            if (playbook != null) {
                return playbook;
            }
        }
        return null;
    }

    public Playbook loadPlaybookById(String playbookId) {
        return playbookCache.computeIfAbsent(playbookId, this::fetchAndParsePlaybook);
    }

    public List<String> resolvePlaybookIdsByCredentialType(String credentialType) {
        switch (credentialType.toUpperCase()) {
            case "AKIA":
                return List.of("aws-access-key-exposed", "aws-iam-role-assumption-abuse");
            case "ASIA":
                return List.of("aws-session-token-leaked", "aws-iam-role-assumption-abuse");
            case "ROOT_":
                return List.of("aws-root-credentials-exposed");
            default:
                return Collections.emptyList();
        }
    }

    private Playbook fetchAndParsePlaybook(String playbookId) {
        if (playbookRepository == null) {
            return loadDefaultPlaybook(playbookId);
        }
        return playbookRepository.findByPlaybookId(playbookId)
                .map(pb -> parsePlaybookContent(pb.getContent()))
                .orElseGet(() -> loadDefaultPlaybook(playbookId));
    }

    private Playbook parsePlaybookContent(String content) {
        try {
            Playbook playbook = YAML_MAPPER.readValue(content, Playbook.class);
            applyDefaults(playbook);
            validatePlaybook(playbook);
            return playbook;
        } catch (Exception e) {
            log.error("Failed to parse playbook content: {}", e.getMessage());
            return null;
        }
    }

    private Playbook loadDefaultPlaybook(String playbookId) {
        return switch (playbookId) {
            case "aws-access-key-exposed" -> defaultAccessKeyExposed();
            case "aws-session-token-leaked" -> defaultSessionTokenLeaked();
            case "aws-root-credentials-exposed" -> defaultRootCredentialsExposed();
            case "aws-iam-role-assumption-abuse" -> defaultIamRoleAssumptionAbuse();
            default -> null;
        };
    }

    private Playbook defaultAccessKeyExposed() {
        Playbook playbook = new Playbook();
        playbook.setPlaybookId("aws-access-key-exposed");
        playbook.setVersion("1.0.0");
        playbook.setCredentialTypes(List.of("AKIA"));
        Map<String, Severidad> floor = new LinkedHashMap<>();
        floor.put("s3_full_access", Severidad.CRITICO);
        floor.put("s3_read_only", Severidad.ALTO);
        floor.put("iam_modify", Severidad.CRITICO);
        floor.put("ec2_instance_control", Severidad.CRITICO);
        floor.put("cloudwatch_read", Severidad.MEDIA);
        floor.put("nothing_active", Severidad.BAJO);
        playbook.setSeverityFloor(floor);
        Playbook.AutoRotate autoRotate = new Playbook.AutoRotate();
        autoRotate.setEnabled(true);
        autoRotate.setMaxWindowMins(15);
        playbook.setAutoRotate(autoRotate);
        playbook.setCanLowerFloor(false);
        return playbook;
    }

    private Playbook defaultSessionTokenLeaked() {
        Playbook playbook = new Playbook();
        playbook.setPlaybookId("aws-session-token-leaked");
        playbook.setVersion("1.0.0");
        playbook.setCredentialTypes(List.of("ASIA"));
        Map<String, Severidad> floor = new LinkedHashMap<>();
        floor.put("assumed_role_admin_full_access", Severidad.CRITICO);
        floor.put("assumed_role_s3_read_write", Severidad.ALTO);
        floor.put("assumed_role_ec2_manage", Severidad.ALTO);
        floor.put("assumed_role_read_only", Severidad.MEDIA);
        floor.put("expired_within_1h", Severidad.BAJO);
        playbook.setSeverityFloor(floor);
        Playbook.AutoRotate autoRotate = new Playbook.AutoRotate();
        autoRotate.setEnabled(false);
        autoRotate.setMaxWindowMins(null);
        playbook.setAutoRotate(autoRotate);
        playbook.setCanLowerFloor(false);
        return playbook;
    }

    private Playbook defaultRootCredentialsExposed() {
        Playbook playbook = new Playbook();
        playbook.setPlaybookId("aws-root-credentials-exposed");
        playbook.setVersion("1.0.0");
        playbook.setCredentialTypes(List.of("ROOT_"));
        Map<String, Severidad> floor = new LinkedHashMap<>();
        floor.put("ec2_active", Severidad.CRITICO);
        floor.put("s3_bucket_active", Severidad.CRITICO);
        floor.put("iam_role_attached", Severidad.CRITICO);
        floor.put("any_resource_active", Severidad.CRITICO);
        floor.put("no_resources_active", Severidad.CRITICO);
        playbook.setSeverityFloor(floor);
        Playbook.AutoRotate autoRotate = new Playbook.AutoRotate();
        autoRotate.setEnabled(true);
        autoRotate.setMaxWindowMins(15);
        playbook.setAutoRotate(autoRotate);
        playbook.setCanLowerFloor(false);
        return playbook;
    }

    private Playbook defaultIamRoleAssumptionAbuse() {
        Playbook playbook = new Playbook();
        playbook.setPlaybookId("aws-iam-role-assumption-abuse");
        playbook.setVersion("1.0.0");
        playbook.setCredentialTypes(List.of("AKIA", "ASIA"));
        Map<String, Severidad> floor = new LinkedHashMap<>();
        floor.put("cross_account_assume_untrusted_trust", Severidad.CRITICO);
        floor.put("admin_role_assumed_from_regular_user", Severidad.CRITICO);
        floor.put("sensitive_data_role_from_external_entity", Severidad.CRITICO);
        floor.put("regular_role_from_internal_source_verified", Severidad.MEDIA);
        floor.put("orphaned_role_no_attached_policies", Severidad.BAJO);
        playbook.setSeverityFloor(floor);
        Playbook.AutoRotate autoRotate = new Playbook.AutoRotate();
        autoRotate.setEnabled(false);
        autoRotate.setMaxWindowMins(null);
        playbook.setAutoRotate(autoRotate);
        playbook.setCanLowerFloor(false);
        return playbook;
    }

    private void applyDefaults(Playbook playbook) {
        if (playbook.getAutoRotate() == null) {
            Playbook.AutoRotate autoRotate = new Playbook.AutoRotate();
            autoRotate.setEnabled(false);
            autoRotate.setMaxWindowMins(60);
            playbook.setAutoRotate(autoRotate);
        } else {
            if (playbook.getAutoRotate().getEnabled() == null) {
                playbook.getAutoRotate().setEnabled(false);
            }
            if (playbook.getAutoRotate().getMaxWindowMins() == null) {
                playbook.getAutoRotate().setMaxWindowMins(60);
            }
        }
        if (playbook.getCanLowerFloor() == null) {
            playbook.setCanLowerFloor(false);
        }
    }

    public void validatePlaybook(Playbook playbook) {
        applyDefaults(playbook);
        Objects.requireNonNull(playbook.getPlaybookId(), "playbook_id is mandatory");
        Objects.requireNonNull(playbook.getVersion(), "version is mandatory");
        Objects.requireNonNull(playbook.getCredentialTypes(), "credential_types is mandatory");
        Objects.requireNonNull(playbook.getSeverityFloor(), "severity_floor is mandatory");
        if (playbook.getCredentialTypes().isEmpty()) {
            throw new IllegalArgumentException("credential_types must not be empty");
        }
        if (playbook.getSeverityFloor().isEmpty()) {
            throw new IllegalArgumentException("severity_floor must not be empty");
        }
    }

    public void reloadAllPlaybooks() {
        playbookCache.clear();
        playbookRepository.findAll().forEach(pb -> {
            Playbook parsed = parsePlaybookContent(pb.getContent());
            if (parsed != null) {
                playbookCache.put(pb.getPlaybookId(), parsed);
            }
        });
    }
}
