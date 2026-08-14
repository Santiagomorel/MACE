package com.company.rotations.verification.enumeration;

import com.company.rotations.verification.model.PermissionMatrix;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PermissionMatrix Tests (ALLOW minus DENY)")
class PermissionMatrixTest {

    private PermissionMatrix matrix;

    @BeforeEach
    void setUp() {
        matrix = new PermissionMatrix();
    }

    @Nested
    @DisplayName("Basic ALLOW/DENY operations")
    class BasicOperationsTests {

        @Test
        @DisplayName("Should add allowed actions")
        void addAllowedActions() {
            matrix.addAllowed("s3:GetObject");
            matrix.addAllowed("s3:PutObject");

            assertTrue(matrix.getAllowedActions().contains("s3:GetObject"));
            assertTrue(matrix.getAllowedActions().contains("s3:PutObject"));
            assertEquals(2, matrix.getAllowedActions().size());
        }

        @Test
        @DisplayName("Should add denied actions")
        void addDeniedActions() {
            matrix.addDenied("s3:DeleteObject");

            assertTrue(matrix.getDeniedActions().contains("s3:DeleteObject"));
            assertEquals(1, matrix.getDeniedActions().size());
        }

        @Test
        @DisplayName("Should exclude DENY from effective actions")
        void denyExcludedFromEffective() {
            matrix.addAllowed("s3:GetObject");
            matrix.addAllowed("s3:PutObject");
            matrix.addDenied("s3:PutObject");

            assertTrue(matrix.getEffectiveActions().contains("s3:GetObject"));
            assertFalse(matrix.getEffectiveActions().contains("s3:PutObject"));
            assertEquals(1, matrix.getEffectiveActions().size());
        }
    }

    @Nested
    @DisplayName("DENY precedence over ALLOW")
    class DenyPrecedenceTests {

        @Test
        @DisplayName("DENY should override ALLOW for same action")
        void denyOverridesAllowForSameAction() {
            matrix.addAllowed("ec2:StartInstances");
            matrix.addDenied("ec2:StartInstances");

            assertFalse(matrix.getEffectiveActions().contains("ec2:StartInstances"));
        }

        @Test
        @DisplayName("Multiple ALLOWs with single DENY")
        void multipleAllowsWithDeny() {
            matrix.addAllowed("s3:GetObject");
            matrix.addAllowed("s3:PutObject");
            matrix.addAllowed("ec2:DescribeInstances");
            matrix.addDenied("s3:PutObject");

            assertEquals(2, matrix.getEffectiveActions().size());
            assertTrue(matrix.getEffectiveActions().contains("s3:GetObject"));
            assertTrue(matrix.getEffectiveActions().contains("ec2:DescribeInstances"));
        }

        @Test
        @DisplayName("DENY on non-existent ALLOW should still be recorded")
        void denyOnNonExistentAllowed() {
            matrix.addDenied("s3:DeleteObject");

            assertTrue(matrix.getDeniedActions().contains("s3:DeleteObject"));
            assertTrue(matrix.getEffectiveActions().isEmpty());
        }
    }

    @Nested
    @DisplayName("Empty permission set handling")
    class EmptyPermissionSetTests {

        @Test
        @DisplayName("Should return empty effective actions when no permissions added")
        void emptyMatrixReturnsEmptyEffective() {
            assertTrue(matrix.getEffectiveActions().isEmpty());
            assertEquals(0, matrix.size());
        }

        @Test
        @DisplayName("Should report isEmpty as true for empty matrix")
        void isEmptyReturnsTrueForEmpty() {
            assertTrue(matrix.isEmpty());
        }

        @Test
        @DisplayName("Should report isEmpty as false after adding allowed")
        void isEmptyReturnsFalseAfterAdding() {
            matrix.addAllowed("s3:GetObject");
            assertFalse(matrix.isEmpty());
        }

        @Test
        @DisplayName("Should return false for isEmpty after ALLOW then DENY")
        void isEmptyReturnsFalseAfterAllowDeny() {
            matrix.addAllowed("s3:GetObject");
            matrix.addDenied("s3:GetObject");
            assertFalse(matrix.isEmpty());
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should ignore null actions")
        void ignoreNullActions() {
            matrix.addAllowed(null);
            matrix.addDenied(null);

            assertTrue(matrix.isEmpty());
        }

        @Test
        @DisplayName("Should ignore blank actions")
        void ignoreBlankActions() {
            matrix.addAllowed("  ");
            matrix.addDenied("  ");

            assertTrue(matrix.isEmpty());
        }

        @Test
        @DisplayName("Should clear all permissions")
        void clearAllPermissions() {
            matrix.addAllowed("s3:GetObject");
            matrix.addDenied("s3:PutObject");
            matrix.clear();

            assertTrue(matrix.getAllowedActions().isEmpty());
            assertTrue(matrix.getDeniedActions().isEmpty());
            assertTrue(matrix.getEffectiveActions().isEmpty());
        }
    }
}
