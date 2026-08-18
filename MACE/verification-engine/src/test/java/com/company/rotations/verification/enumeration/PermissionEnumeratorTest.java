package com.company.rotations.verification.enumeration;

import com.company.rotations.verification.model.PermissionMatrix;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.GetPolicyRequest;
import software.amazon.awssdk.services.iam.model.GetPolicyResponse;
import software.amazon.awssdk.services.iam.model.GetPolicyVersionRequest;
import software.amazon.awssdk.services.iam.model.GetPolicyVersionResponse;
import software.amazon.awssdk.services.iam.model.GetUserPolicyRequest;
import software.amazon.awssdk.services.iam.model.GetUserPolicyResponse;
import software.amazon.awssdk.services.iam.model.Policy;
import software.amazon.awssdk.services.iam.model.PolicyVersion;
import software.amazon.awssdk.services.iam.model.AttachedPolicy;
import software.amazon.awssdk.services.iam.model.ListAttachedUserPoliciesRequest;
import software.amazon.awssdk.services.iam.model.ListAttachedUserPoliciesResponse;
import software.amazon.awssdk.services.iam.model.ListUserPoliciesRequest;
import software.amazon.awssdk.services.iam.model.ListUserPoliciesResponse;
import software.amazon.awssdk.services.iam.model.NoSuchEntityException;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("PermissionEnumerator Tests")
@ExtendWith(MockitoExtension.class)
class PermissionEnumeratorTest {

    @Mock
    private IamClient iamClient;

    private PermissionEnumerator enumerator;

    private static final String SAMPLE_POLICY_DOC = "{\n" +
            "  \"Version\": \"2012-10-17\",\n" +
            "  \"Statement\": [\n" +
            "    {\n" +
            "      \"Effect\": \"Allow\",\n" +
            "      \"Action\": \"s3:GetObject\",\n" +
            "      \"Resource\": \"*\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"Effect\": \"Allow\",\n" +
            "      \"Action\": \"s3:PutObject\",\n" +
            "      \"Resource\": \"*\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"Effect\": \"Deny\",\n" +
            "      \"Action\": \"s3:DeleteObject\",\n" +
            "      \"Resource\": \"*\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";

    private static final String SINGLE_ACTION_POLICY_DOC = "{\n" +
            "  \"Version\": \"2012-10-17\",\n" +
            "  \"Statement\": [\n" +
            "    {\n" +
            "      \"Effect\": \"Allow\",\n" +
            "      \"Action\": \"ec2:DescribeInstances\",\n" +
            "      \"Resource\": \"*\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";

    private static final String DENY_POLICY_DOC = "{\n" +
            "  \"Version\": \"2012-10-17\",\n" +
            "  \"Statement\": [\n" +
            "    {\n" +
            "      \"Effect\": \"Deny\",\n" +
            "      \"Action\": \"iam:DeleteUser\",\n" +
            "      \"Resource\": \"*\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";

    @BeforeEach
    void setUp() {
        enumerator = new PermissionEnumerator(iamClient);
    }

    @Nested
    @DisplayName("Successful permission enumeration")
    class SuccessfulEnumerationTests {

        @Test
        @DisplayName("Should enumerate attached managed policies")
        void enumerateAttachedManagedPolicies() {
            String userName = "test-user";
            String policyArn = "arn:aws:iam::123456789012:policy/TestPolicy";

            ListAttachedUserPoliciesResponse attachedResponse = mock(ListAttachedUserPoliciesResponse.class);
            AttachedPolicy attachedPolicy = mock(AttachedPolicy.class);
            when(attachedPolicy.policyArn()).thenReturn(policyArn);
            when(attachedResponse.attachedPolicies()).thenReturn(Collections.singletonList(attachedPolicy));
            when(iamClient.listAttachedUserPolicies(any(ListAttachedUserPoliciesRequest.class))).thenReturn(attachedResponse);

            Policy policyInfo = mock(Policy.class);
            when(policyInfo.defaultVersionId()).thenReturn("v1");
            GetPolicyResponse getPolicyResponse = mock(GetPolicyResponse.class);
            when(getPolicyResponse.policy()).thenReturn(policyInfo);
            when(iamClient.getPolicy(any(GetPolicyRequest.class))).thenReturn(getPolicyResponse);

            PolicyVersion versionInfo = mock(PolicyVersion.class);
            when(versionInfo.document()).thenReturn(SAMPLE_POLICY_DOC);
            GetPolicyVersionResponse getVersionResponse = mock(GetPolicyVersionResponse.class);
            when(getVersionResponse.policyVersion()).thenReturn(versionInfo);
            when(iamClient.getPolicyVersion(any(GetPolicyVersionRequest.class))).thenReturn(getVersionResponse);

            ListUserPoliciesResponse inlineResponse = mock(ListUserPoliciesResponse.class);
            when(inlineResponse.policyNames()).thenReturn(Collections.emptyList());
            when(iamClient.listUserPolicies(any(ListUserPoliciesRequest.class))).thenReturn(inlineResponse);

            PermissionMatrix result = enumerator.enumeratePermissions(iamClient, "arn:aws:iam::123456789012:user/" + userName);

            assertNotNull(result);
            Set<String> effectiveActions = result.getEffectiveActions();
            assertTrue(effectiveActions.contains("s3:GetObject"));
            assertTrue(effectiveActions.contains("s3:PutObject"));
            assertFalse(effectiveActions.contains("s3:DeleteObject"));
        }

        @Test
        @DisplayName("Should enumerate inline policies")
        void enumerateInlinePolicies() {
            String userName = "test-user";

            ListAttachedUserPoliciesResponse attachedResponse = mock(ListAttachedUserPoliciesResponse.class);
            when(attachedResponse.attachedPolicies()).thenReturn(Collections.emptyList());
            when(iamClient.listAttachedUserPolicies(any(ListAttachedUserPoliciesRequest.class))).thenReturn(attachedResponse);

            ListUserPoliciesResponse inlineResponse = mock(ListUserPoliciesResponse.class);
            when(inlineResponse.policyNames()).thenReturn(Collections.singletonList("MyInlinePolicy"));
            when(iamClient.listUserPolicies(any(ListUserPoliciesRequest.class))).thenReturn(inlineResponse);

            GetUserPolicyResponse userPolicyResponse = mock(GetUserPolicyResponse.class);
            when(userPolicyResponse.policyDocument()).thenReturn(SINGLE_ACTION_POLICY_DOC);
            when(iamClient.getUserPolicy(any(GetUserPolicyRequest.class))).thenReturn(userPolicyResponse);

            PermissionMatrix result = enumerator.enumeratePermissions(iamClient, "arn:aws:iam::123456789012:user/" + userName);

            assertNotNull(result);
            assertTrue(result.getEffectiveActions().contains("ec2:DescribeInstances"));
        }

        @Test
        @DisplayName("Should combine managed and inline policies")
        void combineManagedAndInlinePolicies() {
            String userName = "test-user";
            String policyArn = "arn:aws:iam::123456789012:policy/TestPolicy";

            ListAttachedUserPoliciesResponse attachedResponse = mock(ListAttachedUserPoliciesResponse.class);
            AttachedPolicy attachedPolicy = mock(AttachedPolicy.class);
            when(attachedPolicy.policyArn()).thenReturn(policyArn);
            when(attachedResponse.attachedPolicies()).thenReturn(Collections.singletonList(attachedPolicy));
            when(iamClient.listAttachedUserPolicies(any(ListAttachedUserPoliciesRequest.class))).thenReturn(attachedResponse);

            Policy policyInfo = mock(Policy.class);
            when(policyInfo.defaultVersionId()).thenReturn("v1");
            GetPolicyResponse getPolicyResponse = mock(GetPolicyResponse.class);
            when(getPolicyResponse.policy()).thenReturn(policyInfo);
            when(iamClient.getPolicy(any(GetPolicyRequest.class))).thenReturn(getPolicyResponse);

            PolicyVersion versionInfo = mock(PolicyVersion.class);
            when(versionInfo.document()).thenReturn(DENY_POLICY_DOC);
            GetPolicyVersionResponse getVersionResponse = mock(GetPolicyVersionResponse.class);
            when(getVersionResponse.policyVersion()).thenReturn(versionInfo);
            when(iamClient.getPolicyVersion(any(GetPolicyVersionRequest.class))).thenReturn(getVersionResponse);

            ListUserPoliciesResponse inlineResponse = mock(ListUserPoliciesResponse.class);
            when(inlineResponse.policyNames()).thenReturn(Collections.singletonList("DenyPolicy"));
            when(iamClient.listUserPolicies(any(ListUserPoliciesRequest.class))).thenReturn(inlineResponse);

            GetUserPolicyResponse userPolicyResponse = mock(GetUserPolicyResponse.class);
            when(userPolicyResponse.policyDocument()).thenReturn(SINGLE_ACTION_POLICY_DOC);
            when(iamClient.getUserPolicy(any(GetUserPolicyRequest.class))).thenReturn(userPolicyResponse);

            PermissionMatrix result = enumerator.enumeratePermissions(iamClient, "arn:aws:iam::123456789012:user/" + userName);

            assertNotNull(result);
            assertTrue(result.getDeniedActions().contains("iam:DeleteUser"));
            assertTrue(result.getEffectiveActions().contains("ec2:DescribeInstances"));
        }

        @Test
        @DisplayName("Should handle no policies attached and no inline policies")
        void handleNoPolicies() {
            ListAttachedUserPoliciesResponse attachedResponse = mock(ListAttachedUserPoliciesResponse.class);
            when(attachedResponse.attachedPolicies()).thenReturn(Collections.emptyList());
            when(iamClient.listAttachedUserPolicies(any(ListAttachedUserPoliciesRequest.class))).thenReturn(attachedResponse);

            ListUserPoliciesResponse inlineResponse = mock(ListUserPoliciesResponse.class);
            when(inlineResponse.policyNames()).thenReturn(Collections.emptyList());
            when(iamClient.listUserPolicies(any(ListUserPoliciesRequest.class))).thenReturn(inlineResponse);

            PermissionMatrix result = enumerator.enumeratePermissions(iamClient, "arn:aws:iam::123456789012:user/test-user");

            assertNotNull(result);
            assertTrue(result.getEffectiveActions().isEmpty());
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should return empty matrix on NoSuchEntityException")
        void handleNoSuchEntityException() {
            when(iamClient.listAttachedUserPolicies(any(ListAttachedUserPoliciesRequest.class)))
                    .thenThrow(NoSuchEntityException.builder().message("User not found").build());

            PermissionMatrix result = enumerator.enumeratePermissions(iamClient, "arn:aws:iam::123456789012:user/nonexistent");

            assertNotNull(result);
            assertTrue(result.getEffectiveActions().isEmpty());
        }

        @Test
        @DisplayName("Should return empty matrix on generic exception")
        void handleGenericException() {
            when(iamClient.listAttachedUserPolicies(any(ListAttachedUserPoliciesRequest.class)))
                    .thenThrow(new RuntimeException("Network error"));

            PermissionMatrix result = enumerator.enumeratePermissions(iamClient, "arn:aws:iam::123456789012:user/test-user");

            assertNotNull(result);
            assertTrue(result.getEffectiveActions().isEmpty());
        }

        @Test
        @DisplayName("Should handle missing default version in policy")
        void handleMissingDefaultVersion() {
            String userName = "test-user";
            String policyArn = "arn:aws:iam::123456789012:policy/TestPolicy";

            ListAttachedUserPoliciesResponse attachedResponse = mock(ListAttachedUserPoliciesResponse.class);
            AttachedPolicy attachedPolicy = mock(AttachedPolicy.class);
            when(attachedPolicy.policyArn()).thenReturn(policyArn);
            when(attachedResponse.attachedPolicies()).thenReturn(Collections.singletonList(attachedPolicy));
            when(iamClient.listAttachedUserPolicies(any(ListAttachedUserPoliciesRequest.class))).thenReturn(attachedResponse);

            Policy policyInfo = mock(Policy.class);
            when(policyInfo.defaultVersionId()).thenReturn(null);
            GetPolicyResponse getPolicyResponse = mock(GetPolicyResponse.class);
            when(getPolicyResponse.policy()).thenReturn(policyInfo);
            when(iamClient.getPolicy(any(GetPolicyRequest.class))).thenReturn(getPolicyResponse);

            ListUserPoliciesResponse inlineResponse = mock(ListUserPoliciesResponse.class);
            when(inlineResponse.policyNames()).thenReturn(Collections.emptyList());
            when(iamClient.listUserPolicies(any(ListUserPoliciesRequest.class))).thenReturn(inlineResponse);

            PermissionMatrix result = enumerator.enumeratePermissions(iamClient, "arn:aws:iam::123456789012:user/" + userName);

            assertNotNull(result);
            assertTrue(result.getEffectiveActions().isEmpty());
        }

        @Test
        @DisplayName("Should handle null policy version document")
        void handleNullPolicyVersion() {
            String userName = "test-user";
            String policyArn = "arn:aws:iam::123456789012:policy/TestPolicy";

            ListAttachedUserPoliciesResponse attachedResponse = mock(ListAttachedUserPoliciesResponse.class);
            AttachedPolicy attachedPolicy = mock(AttachedPolicy.class);
            when(attachedPolicy.policyArn()).thenReturn(policyArn);
            when(attachedResponse.attachedPolicies()).thenReturn(Collections.singletonList(attachedPolicy));
            when(iamClient.listAttachedUserPolicies(any(ListAttachedUserPoliciesRequest.class))).thenReturn(attachedResponse);

            Policy policyInfo = mock(Policy.class);
            when(policyInfo.defaultVersionId()).thenReturn("v1");
            GetPolicyResponse getPolicyResponse = mock(GetPolicyResponse.class);
            when(getPolicyResponse.policy()).thenReturn(policyInfo);
            when(iamClient.getPolicy(any(GetPolicyRequest.class))).thenReturn(getPolicyResponse);

            GetPolicyVersionResponse getVersionResponse = mock(GetPolicyVersionResponse.class);
            when(getVersionResponse.policyVersion()).thenReturn(null);
            when(iamClient.getPolicyVersion(any(GetPolicyVersionRequest.class))).thenReturn(getVersionResponse);

            ListUserPoliciesResponse inlineResponse = mock(ListUserPoliciesResponse.class);
            when(inlineResponse.policyNames()).thenReturn(Collections.emptyList());
            when(iamClient.listUserPolicies(any(ListUserPoliciesRequest.class))).thenReturn(inlineResponse);

            PermissionMatrix result = enumerator.enumeratePermissions(iamClient, "arn:aws:iam::123456789012:user/" + userName);

            assertNotNull(result);
            assertTrue(result.getEffectiveActions().isEmpty());
        }

        @Test
        @DisplayName("Should handle NoSuchEntityException on getPolicy")
        void handleNoSuchEntityOnGetPolicy() {
            String userName = "test-user";
            String policyArn = "arn:aws:iam::123456789012:policy/NonexistentPolicy";

            ListAttachedUserPoliciesResponse attachedResponse = mock(ListAttachedUserPoliciesResponse.class);
            AttachedPolicy attachedPolicy = mock(AttachedPolicy.class);
            when(attachedPolicy.policyArn()).thenReturn(policyArn);
            when(attachedResponse.attachedPolicies()).thenReturn(Collections.singletonList(attachedPolicy));
            when(iamClient.listAttachedUserPolicies(any(ListAttachedUserPoliciesRequest.class))).thenReturn(attachedResponse);

            when(iamClient.getPolicy(any(GetPolicyRequest.class)))
                    .thenThrow(NoSuchEntityException.builder().message("Policy not found").build());

            ListUserPoliciesResponse inlineResponse = mock(ListUserPoliciesResponse.class);
            when(inlineResponse.policyNames()).thenReturn(Collections.emptyList());
            when(iamClient.listUserPolicies(any(ListUserPoliciesRequest.class))).thenReturn(inlineResponse);

            PermissionMatrix result = enumerator.enumeratePermissions(iamClient, "arn:aws:iam::123456789012:user/" + userName);

            assertNotNull(result);
            assertTrue(result.getEffectiveActions().isEmpty());
        }

        @Test
        @DisplayName("Should handle NoSuchEntityException on listInlinePolicies")
        void handleNoSuchEntityOnInlinePolicies() {
            String userName = "test-user";
            String policyArn = "arn:aws:iam::123456789012:policy/TestPolicy";

            ListAttachedUserPoliciesResponse attachedResponse = mock(ListAttachedUserPoliciesResponse.class);
            AttachedPolicy attachedPolicy = mock(AttachedPolicy.class);
            when(attachedPolicy.policyArn()).thenReturn(policyArn);
            when(attachedResponse.attachedPolicies()).thenReturn(Collections.singletonList(attachedPolicy));
            when(iamClient.listAttachedUserPolicies(any(ListAttachedUserPoliciesRequest.class))).thenReturn(attachedResponse);

            Policy policyInfo = mock(Policy.class);
            when(policyInfo.defaultVersionId()).thenReturn("v1");
            GetPolicyResponse getPolicyResponse = mock(GetPolicyResponse.class);
            when(getPolicyResponse.policy()).thenReturn(policyInfo);
            when(iamClient.getPolicy(any(GetPolicyRequest.class))).thenReturn(getPolicyResponse);

            PolicyVersion versionInfo = mock(PolicyVersion.class);
            when(versionInfo.document()).thenReturn(SINGLE_ACTION_POLICY_DOC);
            GetPolicyVersionResponse getVersionResponse = mock(GetPolicyVersionResponse.class);
            when(getVersionResponse.policyVersion()).thenReturn(versionInfo);
            when(iamClient.getPolicyVersion(any(GetPolicyVersionRequest.class))).thenReturn(getVersionResponse);

            when(iamClient.listUserPolicies(any(ListUserPoliciesRequest.class)))
                    .thenThrow(NoSuchEntityException.builder().message("User not found").build());

            PermissionMatrix result = enumerator.enumeratePermissions(iamClient, "arn:aws:iam::123456789012:user/" + userName);

            assertNotNull(result);
            assertTrue(result.getEffectiveActions().contains("ec2:DescribeInstances"));
        }

        @Test
        @DisplayName("Should handle NoSuchEntityException on getUserPolicy")
        void handleNoSuchEntityOnGetUserPolicy() {
            String userName = "test-user";

            ListAttachedUserPoliciesResponse attachedResponse = mock(ListAttachedUserPoliciesResponse.class);
            when(attachedResponse.attachedPolicies()).thenReturn(Collections.emptyList());
            when(iamClient.listAttachedUserPolicies(any(ListAttachedUserPoliciesRequest.class))).thenReturn(attachedResponse);

            ListUserPoliciesResponse inlineResponse = mock(ListUserPoliciesResponse.class);
            when(inlineResponse.policyNames()).thenReturn(Collections.singletonList("MissingPolicy"));
            when(iamClient.listUserPolicies(any(ListUserPoliciesRequest.class))).thenReturn(inlineResponse);

            when(iamClient.getUserPolicy(any(GetUserPolicyRequest.class)))
                    .thenThrow(NoSuchEntityException.builder().message("Inline policy not found").build());

            PermissionMatrix result = enumerator.enumeratePermissions(iamClient, "arn:aws:iam::123456789012:user/" + userName);

            assertNotNull(result);
            assertTrue(result.getEffectiveActions().isEmpty());
        }
    }

    @Nested
    @DisplayName("Edge cases and null handling")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should return empty matrix for null identity ARN")
        void nullIdentityArn() {
            PermissionMatrix result = enumerator.enumeratePermissions(iamClient, null);

            assertNotNull(result);
            assertTrue(result.getEffectiveActions().isEmpty());
        }

        @Test
        @DisplayName("Should return empty matrix for malformed ARN without slash")
        void malformedArnWithoutSlash() {
            PermissionMatrix result = enumerator.enumeratePermissions(iamClient, "malformed-arn");

            assertNotNull(result);
            assertTrue(result.getEffectiveActions().isEmpty());
        }

        @Test
        @DisplayName("Should extract username from complex ARN path")
        void extractFromComplexArn() {
            ListAttachedUserPoliciesResponse attachedResponse = mock(ListAttachedUserPoliciesResponse.class);
            when(attachedResponse.attachedPolicies()).thenReturn(Collections.emptyList());
            when(iamClient.listAttachedUserPolicies(any(ListAttachedUserPoliciesRequest.class))).thenReturn(attachedResponse);

            ListUserPoliciesResponse inlineResponse = mock(ListUserPoliciesResponse.class);
            when(inlineResponse.policyNames()).thenReturn(Collections.emptyList());
            when(iamClient.listUserPolicies(any(ListUserPoliciesRequest.class))).thenReturn(inlineResponse);

            PermissionMatrix result = enumerator.enumeratePermissions(
                    iamClient, "arn:aws:iam::123456789012:user/some/path/test-user");

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should handle policy document with no Statement field")
        void policyDocNoStatement() {
            String userName = "test-user";
            String policyArn = "arn:aws:iam::123456789012:policy/TestPolicy";

            ListAttachedUserPoliciesResponse attachedResponse = mock(ListAttachedUserPoliciesResponse.class);
            AttachedPolicy attachedPolicy = mock(AttachedPolicy.class);
            when(attachedPolicy.policyArn()).thenReturn(policyArn);
            when(attachedResponse.attachedPolicies()).thenReturn(Collections.singletonList(attachedPolicy));
            when(iamClient.listAttachedUserPolicies(any(ListAttachedUserPoliciesRequest.class))).thenReturn(attachedResponse);

            Policy policyInfo = mock(Policy.class);
            when(policyInfo.defaultVersionId()).thenReturn("v1");
            GetPolicyResponse getPolicyResponse = mock(GetPolicyResponse.class);
            when(getPolicyResponse.policy()).thenReturn(policyInfo);
            when(iamClient.getPolicy(any(GetPolicyRequest.class))).thenReturn(getPolicyResponse);

            PolicyVersion versionInfo = mock(PolicyVersion.class);
            when(versionInfo.document()).thenReturn("{\"Version\": \"2012-10-17\"}");
            GetPolicyVersionResponse getVersionResponse = mock(GetPolicyVersionResponse.class);
            when(getVersionResponse.policyVersion()).thenReturn(versionInfo);
            when(iamClient.getPolicyVersion(any(GetPolicyVersionRequest.class))).thenReturn(getVersionResponse);

            ListUserPoliciesResponse inlineResponse = mock(ListUserPoliciesResponse.class);
            when(inlineResponse.policyNames()).thenReturn(Collections.emptyList());
            when(iamClient.listUserPolicies(any(ListUserPoliciesRequest.class))).thenReturn(inlineResponse);

            PermissionMatrix result = enumerator.enumeratePermissions(iamClient, "arn:aws:iam::123456789012:user/" + userName);

            assertNotNull(result);
            assertTrue(result.getEffectiveActions().isEmpty());
        }

        @Test
        @DisplayName("Should handle policy document with invalid JSON")
        void invalidPolicyJson() {
            String userName = "test-user";
            String policyArn = "arn:aws:iam::123456789012:policy/TestPolicy";

            ListAttachedUserPoliciesResponse attachedResponse = mock(ListAttachedUserPoliciesResponse.class);
            AttachedPolicy attachedPolicy = mock(AttachedPolicy.class);
            when(attachedPolicy.policyArn()).thenReturn(policyArn);
            when(attachedResponse.attachedPolicies()).thenReturn(Collections.singletonList(attachedPolicy));
            when(iamClient.listAttachedUserPolicies(any(ListAttachedUserPoliciesRequest.class))).thenReturn(attachedResponse);

            Policy policyInfo = mock(Policy.class);
            when(policyInfo.defaultVersionId()).thenReturn("v1");
            GetPolicyResponse getPolicyResponse = mock(GetPolicyResponse.class);
            when(getPolicyResponse.policy()).thenReturn(policyInfo);
            when(iamClient.getPolicy(any(GetPolicyRequest.class))).thenReturn(getPolicyResponse);

            PolicyVersion versionInfo = mock(PolicyVersion.class);
            when(versionInfo.document()).thenReturn("{invalid json}");
            GetPolicyVersionResponse getVersionResponse = mock(GetPolicyVersionResponse.class);
            when(getVersionResponse.policyVersion()).thenReturn(versionInfo);
            when(iamClient.getPolicyVersion(any(GetPolicyVersionRequest.class))).thenReturn(getVersionResponse);

            ListUserPoliciesResponse inlineResponse = mock(ListUserPoliciesResponse.class);
            when(inlineResponse.policyNames()).thenReturn(Collections.emptyList());
            when(iamClient.listUserPolicies(any(ListUserPoliciesRequest.class))).thenReturn(inlineResponse);

            PermissionMatrix result = enumerator.enumeratePermissions(iamClient, "arn:aws:iam::123456789012:user/" + userName);

            assertNotNull(result);
            assertTrue(result.getEffectiveActions().isEmpty());
        }

        @Test
        @DisplayName("Should handle policy document with null effect")
        void nullEffectSkipped() {
            String userName = "test-user";
            String policyArn = "arn:aws:iam::123456789012:policy/TestPolicy";

            ListAttachedUserPoliciesResponse attachedResponse = mock(ListAttachedUserPoliciesResponse.class);
            AttachedPolicy attachedPolicy = mock(AttachedPolicy.class);
            when(attachedPolicy.policyArn()).thenReturn(policyArn);
            when(attachedResponse.attachedPolicies()).thenReturn(Collections.singletonList(attachedPolicy));
            when(iamClient.listAttachedUserPolicies(any(ListAttachedUserPoliciesRequest.class))).thenReturn(attachedResponse);

            Policy policyInfo = mock(Policy.class);
            when(policyInfo.defaultVersionId()).thenReturn("v1");
            GetPolicyResponse getPolicyResponse = mock(GetPolicyResponse.class);
            when(getPolicyResponse.policy()).thenReturn(policyInfo);
            when(iamClient.getPolicy(any(GetPolicyRequest.class))).thenReturn(getPolicyResponse);

            PolicyVersion versionInfo = mock(PolicyVersion.class);
            when(versionInfo.document()).thenReturn("{\"Statement\": [{\"Action\": \"s3:GetObject\"}]}");
            GetPolicyVersionResponse getVersionResponse = mock(GetPolicyVersionResponse.class);
            when(getVersionResponse.policyVersion()).thenReturn(versionInfo);
            when(iamClient.getPolicyVersion(any(GetPolicyVersionRequest.class))).thenReturn(getVersionResponse);

            ListUserPoliciesResponse inlineResponse = mock(ListUserPoliciesResponse.class);
            when(inlineResponse.policyNames()).thenReturn(Collections.emptyList());
            when(iamClient.listUserPolicies(any(ListUserPoliciesRequest.class))).thenReturn(inlineResponse);

            PermissionMatrix result = enumerator.enumeratePermissions(iamClient, "arn:aws:iam::123456789012:user/" + userName);

            assertNotNull(result);
            assertTrue(result.getEffectiveActions().isEmpty());
        }

        @Test
        @DisplayName("Should handle inline policy with null document")
        void nullInlinePolicyDocument() {
            String userName = "test-user";

            ListAttachedUserPoliciesResponse attachedResponse = mock(ListAttachedUserPoliciesResponse.class);
            when(attachedResponse.attachedPolicies()).thenReturn(Collections.emptyList());
            when(iamClient.listAttachedUserPolicies(any(ListAttachedUserPoliciesRequest.class))).thenReturn(attachedResponse);

            ListUserPoliciesResponse inlineResponse = mock(ListUserPoliciesResponse.class);
            when(inlineResponse.policyNames()).thenReturn(Collections.singletonList("NoDocPolicy"));
            when(iamClient.listUserPolicies(any(ListUserPoliciesRequest.class))).thenReturn(inlineResponse);

            GetUserPolicyResponse userPolicyResponse = mock(GetUserPolicyResponse.class);
            when(userPolicyResponse.policyDocument()).thenReturn(null);
            when(iamClient.getUserPolicy(any(GetUserPolicyRequest.class))).thenReturn(userPolicyResponse);

            PermissionMatrix result = enumerator.enumeratePermissions(iamClient, "arn:aws:iam::123456789012:user/" + userName);

            assertNotNull(result);
            assertTrue(result.getEffectiveActions().isEmpty());
        }
    }

    @Nested
    @DisplayName("Multiple policy handling")
    class MultiplePolicyTests {

        @Test
        @DisplayName("Should handle multiple attached policies")
        void multipleAttachedPolicies() {
            String userName = "test-user";
            String policyArn1 = "arn:aws:iam::123456789012:policy/Policy1";
            String policyArn2 = "arn:aws:iam::123456789012:policy/Policy2";

            ListAttachedUserPoliciesResponse attachedResponse = mock(ListAttachedUserPoliciesResponse.class);
            AttachedPolicy policy1 = mock(AttachedPolicy.class);
            when(policy1.policyArn()).thenReturn(policyArn1);
            AttachedPolicy policy2 = mock(AttachedPolicy.class);
            when(policy2.policyArn()).thenReturn(policyArn2);
            when(attachedResponse.attachedPolicies()).thenReturn(Arrays.asList(policy1, policy2));
            when(iamClient.listAttachedUserPolicies(any(ListAttachedUserPoliciesRequest.class))).thenReturn(attachedResponse);

            Policy policyInfo1 = mock(Policy.class);
            when(policyInfo1.defaultVersionId()).thenReturn("v1");
            GetPolicyResponse getPolicyResponse1 = mock(GetPolicyResponse.class);
            when(getPolicyResponse1.policy()).thenReturn(policyInfo1);

            when(iamClient.getPolicy(any(GetPolicyRequest.class)))
                    .thenAnswer(inv -> {
                        GetPolicyRequest req = inv.getArgument(0);
                        if (req.policyArn().equals(policyArn1)) {
                            return getPolicyResponse1;
                        }
                        GetPolicyResponse getPolicyResponse2 = mock(GetPolicyResponse.class);
                        Policy policyInfo2 = mock(Policy.class);
                        when(policyInfo2.defaultVersionId()).thenReturn("v1");
                        when(getPolicyResponse2.policy()).thenReturn(policyInfo2);
                        return getPolicyResponse2;
                    });

            when(iamClient.getPolicyVersion(any(GetPolicyVersionRequest.class)))
                    .thenAnswer(inv -> {
                        GetPolicyVersionResponse getVersionResponse2 = mock(GetPolicyVersionResponse.class);
                        PolicyVersion versionInfo2 = mock(PolicyVersion.class);
                        when(versionInfo2.document()).thenReturn(SINGLE_ACTION_POLICY_DOC);
                        when(getVersionResponse2.policyVersion()).thenReturn(versionInfo2);
                        return getVersionResponse2;
                    });

            ListUserPoliciesResponse inlineResponse = mock(ListUserPoliciesResponse.class);
            when(inlineResponse.policyNames()).thenReturn(Collections.emptyList());
            when(iamClient.listUserPolicies(any(ListUserPoliciesRequest.class))).thenReturn(inlineResponse);

            PermissionMatrix result = enumerator.enumeratePermissions(iamClient, "arn:aws:iam::123456789012:user/" + userName);

            assertNotNull(result);
            assertEquals(1, result.getEffectiveActions().size());
            assertTrue(result.getEffectiveActions().contains("ec2:DescribeInstances"));
        }

        @Test
        @DisplayName("Should handle multiple inline policies")
        void multipleInlinePolicies() {
            String userName = "test-user";

            ListAttachedUserPoliciesResponse attachedResponse = mock(ListAttachedUserPoliciesResponse.class);
            when(attachedResponse.attachedPolicies()).thenReturn(Collections.emptyList());
            when(iamClient.listAttachedUserPolicies(any(ListAttachedUserPoliciesRequest.class))).thenReturn(attachedResponse);

            ListUserPoliciesResponse inlineResponse = mock(ListUserPoliciesResponse.class);
            when(inlineResponse.policyNames()).thenReturn(Arrays.asList("Policy1", "Policy2"));
            when(iamClient.listUserPolicies(any(ListUserPoliciesRequest.class))).thenReturn(inlineResponse);

            when(iamClient.getUserPolicy(any(GetUserPolicyRequest.class)))
                    .thenAnswer(inv -> {
                        GetUserPolicyResponse response = mock(GetUserPolicyResponse.class);
                        when(response.policyDocument()).thenReturn(SINGLE_ACTION_POLICY_DOC);
                        return response;
                    });

            PermissionMatrix result = enumerator.enumeratePermissions(iamClient, "arn:aws:iam::123456789012:user/" + userName);

            assertNotNull(result);
            assertTrue(result.getEffectiveActions().contains("ec2:DescribeInstances"));
        }
    }
}
