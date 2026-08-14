package com.company.rotations.verification.enumeration;

import com.company.rotations.verification.model.PermissionMatrix;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.GetPolicyRequest;
import software.amazon.awssdk.services.iam.model.GetPolicyResponse;
import software.amazon.awssdk.services.iam.model.GetPolicyVersionRequest;
import software.amazon.awssdk.services.iam.model.GetPolicyVersionResponse;
import software.amazon.awssdk.services.iam.model.GetUserPolicyRequest;
import software.amazon.awssdk.services.iam.model.GetUserPolicyResponse;
import software.amazon.awssdk.services.iam.model.ListAttachedUserPoliciesRequest;
import software.amazon.awssdk.services.iam.model.ListAttachedUserPoliciesResponse;
import software.amazon.awssdk.services.iam.model.ListUserPoliciesRequest;
import software.amazon.awssdk.services.iam.model.ListUserPoliciesResponse;
import software.amazon.awssdk.services.iam.model.NoSuchEntityException;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

import java.util.*;

@Service
public class PermissionEnumerator {

    private static final Logger logger = LoggerFactory.getLogger(PermissionEnumerator.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final IamClient iamClient;

    public PermissionEnumerator(IamClient iamClient) {
        this.iamClient = iamClient;
    }

    public PermissionMatrix enumeratePermissions(IamClient iamClient, String identityArn) {
        logger.info("Starting permission enumeration for identity: {}", identityArn);

        PermissionMatrix matrix = new PermissionMatrix();

        try {
            String userName = extractUserName(identityArn);
            if (userName == null) {
                logger.warn("Could not extract username from ARN: {}", identityArn);
                return matrix;
            }

            List<String> attachedPolicies = listAttachedPolicies(iamClient, userName);
            for (String policyArn : attachedPolicies) {
                parseManagedPolicy(iamClient, policyArn, matrix);
            }

            List<String> inlinePolicyNames = listInlinePolicies(iamClient, userName);
            for (String policyName : inlinePolicyNames) {
                parseInlinePolicy(iamClient, userName, policyName, matrix);
            }

            logger.info("Permission enumeration complete for {}: {} effective actions",
                    identityArn, matrix.size());

        } catch (NoSuchEntityException e) {
            logger.warn("Identity not found: {}", identityArn);
        } catch (Exception e) {
            logger.error("Error enumerating permissions for {}: {}", identityArn, e.getMessage());
        }

        return matrix;
    }

    private String extractUserName(String identityArn) {
        if (identityArn == null) return null;
        String[] parts = identityArn.split("/");
        if (parts.length >= 2) {
            return parts[parts.length - 1];
        }
        return null;
    }

    private List<String> listAttachedPolicies(IamClient client, String userName) {
        List<String> policyArns = new ArrayList<>();

        try {
            ListAttachedUserPoliciesResponse response = client.listAttachedUserPolicies(
                    ListAttachedUserPoliciesRequest.builder()
                            .userName(userName)
                            .build());

            response.attachedPolicies().forEach(policy ->
                    policyArns.add(policy.policyArn()));

            logger.debug("Found {} attached policies for user: {}", policyArns.size(), userName);
        } catch (NoSuchEntityException e) {
            logger.warn("User {} not found when listing attached policies", userName);
        }

        return policyArns;
    }

    private List<String> listInlinePolicies(IamClient client, String userName) {
        List<String> policyNames = new ArrayList<>();

        try {
            ListUserPoliciesResponse response = client.listUserPolicies(
                    ListUserPoliciesRequest.builder()
                            .userName(userName)
                            .build());

            response.policyNames().forEach(policyNames::add);

            logger.debug("Found {} inline policies for user: {}", policyNames.size(), userName);
        } catch (NoSuchEntityException e) {
            logger.warn("User {} not found when listing inline policies", userName);
        }

        return policyNames;
    }

    private void parseManagedPolicy(IamClient client, String policyArn, PermissionMatrix matrix) {
        try {
            GetPolicyResponse policyResponse = client.getPolicy(
                    GetPolicyRequest.builder().policyArn(policyArn).build());

            String defaultVersionId = policyResponse.policy() != null ? policyResponse.policy().defaultVersionId() : null;
            if (defaultVersionId == null) return;

            GetPolicyVersionResponse versionResponse = client.getPolicyVersion(
                    GetPolicyVersionRequest.builder()
                            .policyArn(policyArn)
                            .versionId(defaultVersionId)
                            .build());

            String policyDoc = versionResponse.policyVersion() != null
                    ? versionResponse.policyVersion().document()
                    : null;
            parsePolicyDocument(policyDoc, matrix);
        } catch (NoSuchEntityException e) {
            logger.warn("Policy or version not found: {}", policyArn);
        }
    }

    private void parseInlinePolicy(IamClient client, String userName, String policyName, PermissionMatrix matrix) {
        try {
            GetUserPolicyResponse response = client.getUserPolicy(
                    GetUserPolicyRequest.builder()
                            .userName(userName)
                            .policyName(policyName)
                            .build());

            String policyDocument = response.policyDocument();
            if (policyDocument != null) {
                parsePolicyDocument(policyDocument, matrix);
                logger.debug("Parsed inline policy '{}' for user {}: {} statements",
                        policyName, userName, countStatements(policyDocument));
            }
        } catch (NoSuchEntityException e) {
            logger.warn("Inline policy not found: {} for user {}", policyName, userName);
        }
    }

    private void parsePolicyDocument(String policyDocumentJson, PermissionMatrix matrix) {
        try {
            JsonNode rootNode = objectMapper.readTree(policyDocumentJson);
            JsonNode statements = rootNode.path("Statement");

            if (!statements.isArray()) {
                logger.debug("No Statement array found in policy document");
                return;
            }

            for (JsonNode statement : statements) {
                String effect = extractField(statement, "Effect");
                JsonNode actions = statement.path("Action");

                if (effect == null) continue;

                if (actions.isArray()) {
                    for (JsonNode action : actions) {
                        String actionStr = action.asText();
                        if ("Allow".equalsIgnoreCase(effect)) {
                            matrix.addAllowed(actionStr);
                        } else if ("Deny".equalsIgnoreCase(effect)) {
                            matrix.addDenied(actionStr);
                        }
                    }
                } else if (actions.isTextual()) {
                    String actionStr = actions.asText();
                    if ("Allow".equalsIgnoreCase(effect)) {
                        matrix.addAllowed(actionStr);
                    } else if ("Deny".equalsIgnoreCase(effect)) {
                        matrix.addDenied(actionStr);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing policy document: {}", e.getMessage());
        }
    }

    private String extractField(JsonNode node, String fieldName) {
        JsonNode field = node.path(fieldName);
        return field.isTextual() ? field.asText() : null;
    }

    private int countStatements(String policyDocumentJson) {
        try {
            JsonNode rootNode = objectMapper.readTree(policyDocumentJson);
            JsonNode statements = rootNode.path("Statement");
            return statements.isArray() ? statements.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
