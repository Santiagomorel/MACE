package com.company.rotations.verification.provider;

import com.company.rotations.verification.model.ProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProviderDetector Tests")
class ProviderDetectorTest {

    private ProviderDetector detector;

    @BeforeEach
    void setUp() {
        detector = new ProviderDetector();
    }

    @Nested
    @DisplayName("Provider detection from alert source")
    class ProviderFromAlertTests {

        @Test
        @DisplayName("Should detect AWS from 'aws' provider name")
        void detectAwsFromAlert() {
            assertEquals(ProviderType.AWS, detector.detectProvider("aws", null));
        }

        @Test
        @DisplayName("Should detect AWS from 'Amazon' provider name")
        void detectAwsFromAmazonProvider() {
            assertEquals(ProviderType.AWS, detector.detectProvider("Amazon", null));
        }

        @Test
        @DisplayName("Should detect AWS from 'STS' provider name")
        void detectAwsFromStsProvider() {
            assertEquals(ProviderType.AWS, detector.detectProvider("STS", null));
        }

        @Test
        @DisplayName("Should detect Azure from 'azure' provider name")
        void detectAzureFromAlert() {
            assertEquals(ProviderType.AZURE, detector.detectProvider("azure", null));
        }

        @Test
        @DisplayName("Should detect Azure from 'ActiveDirectory' provider name")
        void detectAzureFromAAD() {
            assertEquals(ProviderType.AZURE, detector.detectProvider("ActiveDirectory", null));
        }

        @Test
        @DisplayName("Should detect GCP from 'gcp' provider name")
        void detectGcpFromAlert() {
            assertEquals(ProviderType.GCP, detector.detectProvider("gcp", null));
        }

        @Test
        @DisplayName("Should detect GCP from 'Google' provider name")
        void detectGcpFromGoogle() {
            assertEquals(ProviderType.GCP, detector.detectProvider("Google", null));
        }

        @Test
        @DisplayName("Should return UNKNOWN for unrecognized provider")
        void unknownProviderFromAlert() {
            assertEquals(ProviderType.UNKNOWN, detector.detectProvider("unknown-provider", null));
        }

        @Test
        @DisplayName("Should return UNKNOWN for null provider name")
        void nullProviderReturnsUnknown() {
            assertEquals(ProviderType.UNKNOWN, detector.detectProvider(null, null));
        }

        @Test
        @DisplayName("Should return UNKNOWN for blank provider name")
        void blankProviderReturnsUnknown() {
            assertEquals(ProviderType.UNKNOWN, detector.detectProvider("  ", null));
        }
    }

    @Nested
    @DisplayName("Heuristic provider detection")
    class HeuristicDetectionTests {

        @Test
        @DisplayName("Should detect AWS from AKIA prefix")
        void detectAwsByPrefix() {
            assertEquals(ProviderType.AWS, detector.detectByHeuristic("AKIAIOSFODNN7EXAMPLE"));
        }

        @Test
        @DisplayName("Should detect Azure from eyJ prefix")
        void detectAzureByPrefix() {
            assertEquals(ProviderType.AZURE, detector.detectByHeuristic("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"));
        }

        @Test
        @DisplayName("Should detect GCP from AIzaSy prefix")
        void detectGcpByPrefix() {
            assertEquals(ProviderType.GCP, detector.detectByHeuristic("AIzaSyDExampleKey1234567890"));
        }

        @Test
        @DisplayName("Should return UNKNOWN for empty credential")
        void emptyCredentialReturnsUnknown() {
            assertEquals(ProviderType.UNKNOWN, detector.detectByHeuristic(""));
        }

        @Test
        @DisplayName("Should return UNKNOWN for null credential")
        void nullCredentialReturnsUnknown() {
            assertEquals(ProviderType.UNKNOWN, detector.detectByHeuristic(null));
        }

        @Test
        @DisplayName("Should return UNKNOWN for unrecognized prefix")
        void unknownPrefixReturnsUnknown() {
            assertEquals(ProviderType.UNKNOWN, detector.detectByHeuristic("XYZExample"));
        }

        @Test
        @DisplayName("Should handle whitespace before credential prefix")
        void detectAwsWithWhitespace() {
            assertEquals(ProviderType.UNKNOWN, detector.detectByHeuristic("  AKIAIOSFODNN7EXAMPLE"));
        }
    }

    @Nested
    @DisplayName("Alert source takes precedence over heuristic")
    class PrecedenceTests {

        @Test
        @DisplayName("Alert provider AWS should override heuristic AKIA")
        void alertProviderOverridesHeuristic() {
            assertEquals(ProviderType.AWS, detector.detectProvider("aws", "AKIAIOSFODNN7EXAMPLE"));
        }

        @Test
        @DisplayName("Alert provider Azure should override heuristic AKIA")
        void alertProviderOverridesConflictingHeuristic() {
            assertEquals(ProviderType.AZURE, detector.detectProvider("azure", "AKIAIOSFODNN7EXAMPLE"));
        }

        @Test
        @DisplayName("When alert provider is null, heuristic should be used")
        void fallbackToHeuristicWhenAlertProviderNull() {
            assertEquals(ProviderType.AWS, detector.detectProvider(null, "AKIAIOSFODNN7EXAMPLE"));
        }

        @Test
        @DisplayName("When alert provider is blank, heuristic should be used")
        void fallbackToHeuristicWhenAlertProviderBlank() {
            assertEquals(ProviderType.GCP, detector.detectProvider("  ", "AIzaSyDExampleKey"));
        }
    }
}
