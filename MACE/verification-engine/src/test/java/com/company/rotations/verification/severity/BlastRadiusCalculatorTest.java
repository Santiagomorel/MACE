package com.company.rotations.verification.severity;

import com.company.rotations.verification.model.PermissionMatrix;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BlastRadiusCalculator Tests")
class BlastRadiusCalculatorTest {

    private final BlastRadiusCalculator calculator = new BlastRadiusCalculator();

    @Nested
    @DisplayName("AdministratorAccess → critical")
    class AdministratorAccessTests {

        @Test
        @DisplayName("Should return CRITICAL for wildcard action")
        void shouldReturnCriticalForWildcard() {
            PermissionMatrix matrix = new PermissionMatrix();
            matrix.addAllowed("*");

            assertEquals("CRITICAL", calculator.calculateBlastRadius(matrix));
        }

        @Test
        @DisplayName("Should return CRITICAL for iam:* action")
        void shouldReturnCriticalForIamWildcard() {
            PermissionMatrix matrix = new PermissionMatrix();
            matrix.addAllowed("iam:*");

            assertEquals("CRITICAL", calculator.calculateBlastRadius(matrix));
        }

        @Test
        @DisplayName("Should return CRITICAL for root ARN")
        void shouldReturnCriticalForRootArn() {
            PermissionMatrix matrix = new PermissionMatrix();
            matrix.addAllowed("arn:aws:iam::*:root");

            assertEquals("CRITICAL", calculator.calculateBlastRadius(matrix));
        }
    }

    @Nested
    @DisplayName("ReadOnlyAccess → low")
    class ReadOnlyAccessTests {

        @Test
        @DisplayName("Should return LOW for read-only actions")
        void shouldReturnLowForReadOnly() {
            PermissionMatrix matrix = new PermissionMatrix();
            matrix.addAllowed("s3:ListBucket");
            matrix.addAllowed("s3:GetObject");
            matrix.addAllowed("ec2:DescribeInstances");

            assertEquals("MEDIUM", calculator.calculateBlastRadius(matrix));
        }

        @Test
        @DisplayName("Should return MINIMAL for unknown actions")
        void shouldReturnMinimalForModerateActions() {
            PermissionMatrix matrix = new PermissionMatrix();
            for (int i = 0; i < 25; i++) {
                matrix.addAllowed("custom:Action" + i);
            }

            assertEquals("MINIMAL", calculator.calculateBlastRadius(matrix));
        }
    }

    @Nested
    @DisplayName("Custom policies")
    class CustomPolicyTests {

        @Test
        @DisplayName("Should return HIGH for IAM write actions")
        void shouldReturnHighForIamWrite() {
            PermissionMatrix matrix = new PermissionMatrix();
            matrix.addAllowed("iam:CreateUser");
            matrix.addAllowed("iam:AttachUserPolicy");

            assertEquals("HIGH", calculator.calculateBlastRadius(matrix));
        }

        @Test
        @DisplayName("Should return HIGH for STS AssumeRole")
        void shouldReturnHighForAssumeRole() {
            PermissionMatrix matrix = new PermissionMatrix();
            matrix.addAllowed("sts:AssumeRole");

            assertEquals("HIGH", calculator.calculateBlastRadius(matrix));
        }

        @Test
        @DisplayName("Should return CRITICAL even with some low actions present")
        void shouldReturnCriticalWhenCriticalActionsPresent() {
            PermissionMatrix matrix = new PermissionMatrix();
            matrix.addAllowed("s3:GetObject");
            matrix.addAllowed("*");
            matrix.addAllowed("ec2:DescribeInstances");

            assertEquals("CRITICAL", calculator.calculateBlastRadius(matrix));
        }

        @Test
        @DisplayName("Should return MINIMAL for unrecognized actions")
        void shouldReturnMinimalForUnknownActions() {
            PermissionMatrix matrix = new PermissionMatrix();
            matrix.addAllowed("unknown:Action1");
            matrix.addAllowed("custom:Action2");

            assertEquals("MINIMAL", calculator.calculateBlastRadius(matrix));
        }

        @Test
        @DisplayName("Should return NONE for empty matrix")
        void shouldReturnNoneForEmptyMatrix() {
            PermissionMatrix matrix = new PermissionMatrix();

            assertEquals("NONE", calculator.calculateBlastRadius(matrix));
        }

        @Test
        @DisplayName("Should return NONE for null matrix")
        void shouldReturnNoneForNullMatrix() {
            assertEquals("NONE", calculator.calculateBlastRadius(null));
        }
    }

    @Nested
    @DisplayName("Policy name mappings")
    class PolicyNameMappingsTests {

        @Test
        @DisplayName("Should map AdministratorAccess to CRITICAL")
        void shouldMapAdministratorAccess() {
            assertEquals("CRITICAL", calculator.calculateFromPolicyName("AdministratorAccess"));
        }

        @Test
        @DisplayName("Should map ReadOnlyAccess to LOW")
        void shouldMapReadOnlyAccess() {
            assertEquals("LOW", calculator.calculateFromPolicyName("ReadOnlyAccess"));
        }

        @Test
        @DisplayName("Should map poweruseraccess to HIGH")
        void shouldMapPowerUserAccess() {
            assertEquals("HIGH", calculator.calculateFromPolicyName("PowerUserAccess"));
        }

        @Test
        @DisplayName("Should return UNKNOWN for unrecognized policy")
        void shouldReturnUnknownForUnrecognizedPolicy() {
            assertEquals("UNKNOWN", calculator.calculateFromPolicyName("MyCustomPolicy"));
        }

        @Test
        @DisplayName("Should return UNKNOWN for null policy name")
        void shouldReturnUnknownForNullPolicy() {
            assertEquals("UNKNOWN", calculator.calculateFromPolicyName(null));
        }

        @Test
        @DisplayName("Should return UNKNOWN for blank policy name")
        void shouldReturnUnknownForBlankPolicy() {
            assertEquals("UNKNOWN", calculator.calculateFromPolicyName(""));
        }
    }

    @Nested
    @DisplayName("Action category breakdown")
    class ActionCategoryBreakdownTests {

        @Test
        @DisplayName("Should return category breakdown for mixed actions")
        void shouldReturnCategoryBreakdown() {
            PermissionMatrix matrix = new PermissionMatrix();
            matrix.addAllowed("*");
            matrix.addAllowed("iam:CreateUser");
            matrix.addAllowed("s3:ListBucket");
            matrix.addAllowed("sqs:ReceiveMessage");
            matrix.addAllowed("unknown:Action");

            var breakdown = calculator.getActionCategoryBreakdown(matrix);

            assertEquals(5, breakdown.size());
            assertEquals(1, breakdown.get("CRITICAL"));
            assertEquals(1, breakdown.get("HIGH"));
            assertEquals(1, breakdown.get("MEDIUM"));
            assertEquals(1, breakdown.get("LOW"));
            assertEquals(1, breakdown.get("UNCLASSIFIED"));
        }

        @Test
        @DisplayName("Should return empty map for null matrix")
        void shouldReturnEmptyForNullMatrix() {
            assertTrue(calculator.getActionCategoryBreakdown(null).isEmpty());
        }

        @Test
        @DisplayName("Should return empty map for empty matrix")
        void shouldReturnEmptyForEmptyMatrix() {
            assertTrue(calculator.getActionCategoryBreakdown(new PermissionMatrix()).isEmpty());
        }
    }

    @Nested
    @DisplayName("Risk action sets")
    class RiskActionSetsTests {

        @Test
        @DisplayName("Should expose critical actions")
        void shouldExposeCriticalActions() {
            Set<String> critical = calculator.getCriticalActions();
            assertFalse(critical.isEmpty());
            assertTrue(critical.contains("*"));
            assertTrue(critical.contains("iam:*"));
        }

        @Test
        @DisplayName("Should expose high risk actions")
        void shouldExposeHighRiskActions() {
            Set<String> highRisk = calculator.getHighRiskActions();
            assertFalse(highRisk.isEmpty());
            assertTrue(highRisk.contains("iam:CreateUser"));
        }

        @Test
        @DisplayName("Should expose medium risk actions")
        void shouldExposeMediumRiskActions() {
            Set<String> mediumRisk = calculator.getMediumRiskActions();
            assertFalse(mediumRisk.isEmpty());
            assertTrue(mediumRisk.contains("s3:ListBucket"));
        }

        @Test
        @DisplayName("Should expose low risk actions")
        void shouldExposeLowRiskActions() {
            Set<String> lowRisk = calculator.getLowRiskActions();
            assertFalse(lowRisk.isEmpty());
            assertTrue(lowRisk.contains("sqs:ReceiveMessage"));
        }
    }
}
