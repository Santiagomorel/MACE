package com.company.rotations.verification.severity;

import com.company.rotations.verification.model.PermissionMatrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class BlastRadiusCalculator {

    private static final Logger logger = LoggerFactory.getLogger(BlastRadiusCalculator.class);

    private static final Set<String> CRITICAL_ACTIONS = Set.of(
            "*",
            "iam:*",
            "s3:*",
            "ec2:*",
            "lambda:*",
            "arn:aws:iam::*:root"
    );

    private static final Set<String> HIGH_RISK_ACTIONS = Set.of(
            "iam:CreateUser",
            "iam:CreateAccessKey",
            "iam:AttachUserPolicy",
            "iam:AttachRolePolicy",
            "iam:PutUserPolicy",
            "iam:PutRolePolicy",
            "iam:DeleteUser",
            "iam:DeleteAccessKey",
            "organizations:*",
            "sts:AssumeRole"
    );

    private static final Set<String> MEDIUM_RISK_ACTIONS = Set.of(
            "s3:ListBucket",
            "s3:GetObject",
            "s3:PutObject",
            "ec2:Describe*",
            "ec2:RunInstances",
            "lambda:InvokeFunction",
            "cloudwatch:*"
    );

    private static final Set<String> LOW_RISK_ACTIONS = Set.of(
            "sqs:ReceiveMessage",
            "sns:Subscribe",
            "dynamodb:GetItem",
            "dynamodb:Query",
            "sts:GetSessionToken"
    );

    private final Map<String, String> policyBlastRadiusMap = new LinkedHashMap<>();

    public BlastRadiusCalculator() {
        initializePolicyMappings();
    }

    public String calculateBlastRadius(PermissionMatrix permissionMatrix) {
        if (permissionMatrix == null || permissionMatrix.isEmpty()) {
            logger.debug("Empty permission matrix - blast radius: NONE");
            return "NONE";
        }

        Set<String> effectiveActions = permissionMatrix.getEffectiveActions();
        logger.info("Calculating blast radius for {} effective actions", effectiveActions.size());

        if (hasCriticalActions(effectiveActions)) {
            logger.info("Blast radius: CRITICAL - has critical permissions");
            return "CRITICAL";
        }

        if (hasHighRiskActions(effectiveActions)) {
            logger.info("Blast radius: HIGH - has high-risk permissions");
            return "HIGH";
        }

        if (hasMediumRiskActions(effectiveActions)) {
            logger.info("Blast radius: MEDIUM - has medium-risk permissions");
            return "MEDIUM";
        }

        if (hasLowRiskActions(effectiveActions)) {
            logger.info("Blast radius: LOW - has low-risk permissions");
            return "LOW";
        }

        logger.info("Blast radius: MINIMAL - no recognized risk actions");
        return "MINIMAL";
    }

    public String calculateFromPolicyName(String policyName) {
        if (policyName == null || policyName.isBlank()) {
            return "UNKNOWN";
        }

        String normalized = policyName.toLowerCase();
        if (policyBlastRadiusMap.containsKey(normalized)) {
            String radius = policyBlastRadiusMap.get(normalized);
            logger.debug("Blast radius for policy '{}': {}", policyName, radius);
            return radius;
        }

        logger.debug("No predefined blast radius for policy '{}', calculating from actions", policyName);
        return "UNKNOWN";
    }

    public Map<String, Integer> getActionCategoryBreakdown(PermissionMatrix permissionMatrix) {
        if (permissionMatrix == null || permissionMatrix.isEmpty()) {
            return Map.of();
        }

        Set<String> effectiveActions = permissionMatrix.getEffectiveActions();

        Map<String, Integer> breakdown = new LinkedHashMap<>();
        breakdown.put("CRITICAL", (int) effectiveActions.stream()
                .filter(CRITICAL_ACTIONS::contains)
                .count());
        breakdown.put("HIGH", (int) effectiveActions.stream()
                .filter(HIGH_RISK_ACTIONS::contains)
                .count());
        breakdown.put("MEDIUM", (int) effectiveActions.stream()
                .filter(MEDIUM_RISK_ACTIONS::contains)
                .count());
        breakdown.put("LOW", (int) effectiveActions.stream()
                .filter(LOW_RISK_ACTIONS::contains)
                .count());
        breakdown.put("UNCLASSIFIED", effectiveActions.size()
                - breakdown.get("CRITICAL")
                - breakdown.get("HIGH")
                - breakdown.get("MEDIUM")
                - breakdown.get("LOW"));

        return breakdown;
    }

    public Set<String> getCriticalActions() {
        return Collections.unmodifiableSet(CRITICAL_ACTIONS);
    }

    public Set<String> getHighRiskActions() {
        return Collections.unmodifiableSet(HIGH_RISK_ACTIONS);
    }

    public Set<String> getMediumRiskActions() {
        return Collections.unmodifiableSet(MEDIUM_RISK_ACTIONS);
    }

    public Set<String> getLowRiskActions() {
        return Collections.unmodifiableSet(LOW_RISK_ACTIONS);
    }

    private void initializePolicyMappings() {
        policyBlastRadiusMap.put("administratoraccess", "CRITICAL");
        policyBlastRadiusMap.put("admin", "CRITICAL");
        policyBlastRadiusMap.put("fullaccess", "CRITICAL");
        policyBlastRadiusMap.put("poweruseraccess", "HIGH");
        policyBlastRadiusMap.put("power-user", "HIGH");
        policyBlastRadiusMap.put("readonlyaccess", "LOW");
        policyBlastRadiusMap.put("read-only", "LOW");
        policyBlastRadiusMap.put("viewer", "LOW");
        policyBlastRadiusMap.put("s3fullaccess", "HIGH");
        policyBlastRadiusMap.put("s3readonly", "LOW");
        policyBlastRadiusMap.put("ec2fullaccess", "HIGH");
        policyBlastRadiusMap.put("iamfullaccess", "CRITICAL");
        policyBlastRadiusMap.put("iamreadonly", "LOW");
    }

    private boolean hasCriticalActions(Set<String> actions) {
        return actions.stream()
                .anyMatch(action -> CRITICAL_ACTIONS.stream()
                        .anyMatch(pattern -> matches(action, pattern)));
    }

    private boolean hasHighRiskActions(Set<String> actions) {
        return actions.stream()
                .anyMatch(action -> HIGH_RISK_ACTIONS.stream()
                        .anyMatch(pattern -> matches(action, pattern)));
    }

    private boolean hasMediumRiskActions(Set<String> actions) {
        return actions.stream()
                .anyMatch(action -> MEDIUM_RISK_ACTIONS.stream()
                        .anyMatch(pattern -> matches(action, pattern)));
    }

    private boolean hasLowRiskActions(Set<String> actions) {
        return actions.stream()
                .anyMatch(action -> LOW_RISK_ACTIONS.stream()
                        .anyMatch(pattern -> matches(action, pattern)));
    }

    private boolean matches(String action, String pattern) {
        if (action.equals(pattern)) {
            return true;
        }

        if ("*".equals(pattern)) {
            return action.equals("*");
        }

        if (pattern.endsWith(":*")) {
            return action.equals(pattern);
        }

        if (pattern.endsWith("*")) {
            return action.startsWith(pattern.substring(0, pattern.length() - 1));
        }

        return false;
    }
}
