package com.company.rotations.verification.account.mapper;

import com.company.rotations.verification.model.CredentialAlert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CredentialAlert.AlertContext Tests")
class AlertContextTest {

    @Nested
    @DisplayName("Basic getter and setter operations")
    class BasicGetterSetterTests {

        @Test
        @DisplayName("Should set and get repository")
        void setAndGetRepository() {
            CredentialAlert.AlertContext ctx = new CredentialAlert.AlertContext();
            ctx.setRepository("my-repo");
            assertEquals("my-repo", ctx.getRepository());
        }

        @Test
        @DisplayName("Should set and get file")
        void setAndGetFile() {
            CredentialAlert.AlertContext ctx = new CredentialAlert.AlertContext();
            ctx.setFile("src/credentials.py");
            assertEquals("src/credentials.py", ctx.getFile());
        }

        @Test
        @DisplayName("Should set and get commit")
        void setAndGetCommit() {
            CredentialAlert.AlertContext ctx = new CredentialAlert.AlertContext();
            ctx.setCommit("abc123def456");
            assertEquals("abc123def456", ctx.getCommit());
        }

        @Test
        @DisplayName("Should set and get visibility")
        void setAndGetVisibility() {
            CredentialAlert.AlertContext ctx = new CredentialAlert.AlertContext();
            ctx.setVisibility("public");
            assertEquals("public", ctx.getVisibility());
        }
    }

    @Nested
    @DisplayName("Null value handling")
    class NullValueTests {

        @Test
        @DisplayName("Should return null for all fields by default")
        void defaultValuesNull() {
            CredentialAlert.AlertContext ctx = new CredentialAlert.AlertContext();
            assertNull(ctx.getRepository());
            assertNull(ctx.getFile());
            assertNull(ctx.getCommit());
            assertNull(ctx.getVisibility());
        }
    }

    @Nested
    @DisplayName("Integration with CredentialAlert")
    class IntegrationTests {

        @Test
        @DisplayName("Should be retrievable from CredentialAlert")
        void contextFromAlert() {
            CredentialAlert alert = new CredentialAlert();
            CredentialAlert.AlertContext ctx = new CredentialAlert.AlertContext();
            ctx.setRepository("test-repo");
            ctx.setFile("path/to/file");
            ctx.setCommit("commit123");
            ctx.setVisibility("private");

            alert.setContext(ctx);

            assertNotNull(alert.getContext());
            assertEquals("test-repo", alert.getContext().getRepository());
            assertEquals("path/to/file", alert.getContext().getFile());
            assertEquals("commit123", alert.getContext().getCommit());
            assertEquals("private", alert.getContext().getVisibility());
        }
    }
}
