package com.company.rotations.verification.account.mapper;

import com.company.rotations.verification.model.ProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AccountMapper Tests")
class AccountMapperTest {

    private AccountMapper accountMapper;

    @BeforeEach
    void setUp() {
        accountMapper = new AccountMapper();
    }

    @Nested
    @DisplayName("Hint-based account mapping")
    class HintBasedMappingTests {

        @Test
        @DisplayName("Should use GitGuardian account_hint when available")
        void useHintWhenAvailable() {
            String result = accountMapper.mapAccount("client-account-123", "cred-xyz",
                    Arrays.asList("client-account-123", "client-account-456"), ProviderType.AWS);
            assertEquals("client-account-123", result);
        }

        @Test
        @DisplayName("Should use hint even when it differs from iterative result")
        void hintTakesPrecedenceOverIterative() {
            String result = accountMapper.mapAccount("hint-account", "cred-xyz",
                    Arrays.asList("hint-account", "iterative-account"), ProviderType.AWS);
            assertEquals("hint-account", result);
        }
    }

    @Nested
    @DisplayName("Iterative lookup fallback")
    class IterativeLookupTests {

        @Test
        @DisplayName("Should return null when no hint and no matching account found")
        void noHintNoMatchReturnsNull() {
            String result = accountMapper.mapAccount(null, "cred-xyz",
                    Arrays.asList("account-1", "account-2"), ProviderType.AWS);
            assertNull(result);
        }

        @Test
        @DisplayName("Should return null when account_hint is blank")
        void blankHintReturnsNull() {
            String result = accountMapper.mapAccount("  ", "cred-xyz",
                    Arrays.asList("account-1", "account-2"), ProviderType.AWS);
            assertNull(result);
        }

        @Test
        @DisplayName("Should return null when known accounts list is empty")
        void emptyKnownAccountsReturnsNull() {
            String result = accountMapper.mapAccount(null, "cred-xyz",
                    Collections.emptyList(), ProviderType.AWS);
            assertNull(result);
        }

        @Test
        @DisplayName("Should return null when known accounts list is null")
        void nullKnownAccountsReturnsNull() {
            String result = accountMapper.mapAccount(null, "cred-xyz",
                    null, ProviderType.AWS);
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Provider-specific mapping behavior")
    class ProviderSpecificTests {

        @Test
        @DisplayName("AWS should attempt validation during iterative lookup")
        void awsAttemptsValidation() {
            String result = accountMapper.mapAccount(null, "cred-aws-123",
                    Arrays.asList("account-1", "account-2"), ProviderType.AWS);
            assertNull(result);
        }

        @Test
        @DisplayName("Azure should skip validation (deferred)")
        void azureSkipsValidation() {
            String result = accountMapper.mapAccount(null, "cred-azure-123",
                    Arrays.asList("account-1"), ProviderType.AZURE);
            assertNull(result);
        }

        @Test
        @DisplayName("GCP should skip validation (deferred)")
        void gcpSkipsValidation() {
            String result = accountMapper.mapAccount(null, "cred-gcp-123",
                    Arrays.asList("account-1"), ProviderType.GCP);
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("UNKNOWN status marker scenarios")
    class UnknownAccountTests {

        @Test
        @DisplayName("Should return null for unmapped account to trigger UNKNOWN marker")
        void unmappedAccountReturnsNull() {
            String result = accountMapper.mapAccount(null, "unknown-cred",
                    Arrays.asList("account-1"), ProviderType.AWS);
            assertNull(result);
        }
    }
}
