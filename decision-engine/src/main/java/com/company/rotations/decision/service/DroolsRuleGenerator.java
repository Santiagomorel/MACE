package com.company.rotations.decision.service;

import com.company.rotations.decision.domain.DiscoveredPermissions;
import com.company.rotations.models.Severidad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DroolsRuleGenerator {

    private static final Logger log = LoggerFactory.getLogger(DroolsRuleGenerator.class);

    @Value("${decision.drools.max-drl-size-bytes:51200}")
    private int maxDrlSizeBytes;

    public String generate(String tenantId, DiscoveredPermissions permissions) {
        StringBuilder drl = new StringBuilder();

        // Package declaration with tenant isolation
        drl.append("package com.security.rules.").append(tenantId).append(";\n\n");

        // Compliance tags as comments
        drl.append("// Auto-generated from metadata discovery\n");
        drl.append("// compliance: S3, IAM, EC2, CloudWatch permissions discovery\n\n");

        // Imports
        drl.append("import com.company.rotations.models.Severidad;\n");
        drl.append("import com.company.rotations.models.Alert;\n\n");

        // Generate one rule per detected permission category
        List<String> permissionsList = getDetectedPermissions(permissions);
        int ruleCount = permissionsList.size();
        log.info("Generating {} Drools rules for tenant {} based on detected permissions", ruleCount, tenantId);

        for (int i = 0; i < permissionsList.size(); i++) {
            String permission = permissionsList.get(i);
            Severidad severity = severityForPermission(permission);
            int salience = SeveridadUtil.toSalience(severity);

            drl.append("rule \"").append(tenantId).append("_").append(permission).append("\"\n");
            drl.append("  agenda-group \"rules_").append(tenantId).append("\"\n");
            drl.append("  salience ").append(salience).append(" /* ").append(severity).append(" */\n");
            drl.append("  no-loop true\n");
            drl.append("  lock-on-active true\n");
            drl.append("  when\n");
            drl.append("    Alert( tenantId == \"").append(tenantId).append("\" )\n");
            drl.append("  then\n");
            drl.append("    alert.setSeverity(Severidad.").append(severity).append(");\n");
            drl.append("    alert.setPlaybookId(\"").append(tenantId).append("\");\n");
            drl.append("    insert(alert);\n");
            drl.append("end\n\n");
        }

        validateSize(drl.toString());

        log.info("Generated DRL for tenant {}: {} bytes, {} rules", tenantId, drl.length(), ruleCount);
        return drl.toString();
    }

    private List<String> getDetectedPermissions(DiscoveredPermissions permissions) {
        List<String> detected = new ArrayList<>();
        if (permissions.isS3FullAccess()) detected.add("s3_full_access");
        if (permissions.isS3ReadOnly()) detected.add("s3_read_only");
        if (permissions.isIamModify()) detected.add("iam_modify");
        if (permissions.isEc2InstanceControl()) detected.add("ec2_instance_control");
        if (permissions.isEc2ReadOnly()) detected.add("ec2_read_only");
        if (permissions.isCloudWatchRead()) detected.add("cloudwatch_read");
        if (permissions.isNothingActive()) detected.add("nothing_active");
        return detected;
    }

    private Severidad severityForPermission(String permission) {
        return switch (permission) {
            case "s3_full_access" -> Severidad.CRITICO;
            case "iam_modify" -> Severidad.CRITICO;
            case "ec2_instance_control" -> Severidad.CRITICO;
            case "s3_read_only" -> Severidad.ALTO;
            case "ec2_read_only" -> Severidad.MEDIA;
            case "cloudwatch_read" -> Severidad.MEDIA;
            case "nothing_active" -> Severidad.BAJO;
            default -> Severidad.BAJO;
        };
    }

    private void validateSize(String drl) {
        if (drl.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxDrlSizeBytes) {
            log.warn("Generated DRL exceeds max size: {} bytes (max: {})",
                    drl.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, maxDrlSizeBytes);
        }
    }
}
