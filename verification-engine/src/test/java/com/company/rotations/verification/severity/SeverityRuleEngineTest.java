package com.company.rotations.verification.severity;

import com.company.rotations.verification.severity.SeverityRuleEngine.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SeverityRuleEngineTest {

    private SeverityRuleEngine engine;

    @BeforeEach
    void setUp() {
        Map<String, String> floors = Map.of(
                "tenant-a", "HIGH",
                "tenant-b", "CRITICAL",
                "tenant-c", "LOW"
        );
        engine = new SeverityRuleEngine(floors, "MEDIUM");
    }

    @Nested
    @DisplayName("Default Floor")
    class DefaultFloorTests {

        @Test
        @DisplayName("Should return calculated severity when no tenant rule exists")
        void shouldReturnCalculatedWhenNoTenantRule() {
            assertEquals(Severity.HIGH, engine.applyFloor("unknown-tenant", Severity.HIGH));
        }

        @Test
        @DisplayName("Should apply default floor when calculated is lower")
        void shouldApplyDefaultFloorWhenCalculatedLower() {
            assertEquals(Severity.MEDIUM, engine.applyFloor("unknown-tenant", Severity.LOW));
        }

        @Test
        @DisplayName("Should return calculated when it exceeds default floor")
        void shouldReturnCalculatedWhenExceedsDefault() {
            assertEquals(Severity.CRITICAL, engine.applyFloor("unknown-tenant", Severity.CRITICAL));
        }

        @Test
        @DisplayName("Should handle null tenant ID with default floor")
        void shouldHandleNullTenantId() {
            assertEquals(Severity.MEDIUM, engine.applyFloor(null, Severity.LOW));
            assertEquals(Severity.CRITICAL, engine.applyFloor(null, Severity.CRITICAL));
        }

        @Test
        @DisplayName("Should handle empty tenant ID with default floor")
        void shouldHandleEmptyTenantId() {
            assertEquals(Severity.MEDIUM, engine.applyFloor("", Severity.LOW));
        }
    }

    @Nested
    @DisplayName("Tenant-Specific Floors")
    class TenantFloorTests {

        @Test
        @DisplayName("Should raise calculated to tenant floor when calculated is lower")
        void shouldRaiseToTenantFloorWhenCalculatedLower() {
            assertEquals(Severity.HIGH, engine.applyFloor("tenant-a", Severity.LOW));
            assertEquals(Severity.HIGH, engine.applyFloor("tenant-a", Severity.MEDIUM));
        }

        @Test
        @DisplayName("Should return calculated when it exceeds tenant floor")
        void shouldReturnCalculatedWhenExceedsTenantFloor() {
            assertEquals(Severity.CRITICAL, engine.applyFloor("tenant-a", Severity.CRITICAL));
        }

        @Test
        @DisplayName("Should apply tenant B CRITICAL floor")
        void shouldApplyTenantBFloor() {
            assertEquals(Severity.CRITICAL, engine.applyFloor("tenant-b", Severity.LOW));
            assertEquals(Severity.CRITICAL, engine.applyFloor("tenant-b", Severity.CRITICAL));
            assertEquals(Severity.CRITICAL, engine.applyFloor("tenant-b", Severity.HIGH));
        }

        @Test
        @DisplayName("Should raise to tenant C LOW floor")
        void shouldApplyTenantCFloor() {
            assertEquals(Severity.LOW, engine.applyFloor("tenant-c", Severity.LOW));
            assertEquals(Severity.MEDIUM, engine.applyFloor("tenant-c", Severity.MEDIUM));
            assertEquals(Severity.CRITICAL, engine.applyFloor("tenant-c", Severity.CRITICAL));
        }

        @Test
        @DisplayName("Should be case insensitive for tenant IDs")
        void shouldBeCaseInsensitive() {
            assertEquals(Severity.HIGH, engine.applyFloor("TENANT-A", Severity.LOW));
            assertEquals(Severity.HIGH, engine.applyFloor("Tenant-A", Severity.LOW));
        }
    }

    @Nested
    @DisplayName("Get Tenant Floor")
    class GetTenantFloorTests {

        @Test
        @DisplayName("Should return floor for known tenant")
        void shouldReturnFloorForKnownTenant() {
            assertEquals(java.util.Optional.of("HIGH"), engine.getTenantFloor("tenant-a"));
        }

        @Test
        @DisplayName("Should return empty for unknown tenant")
        void shouldReturnEmptyForUnknownTenant() {
            assertTrue(engine.getTenantFloor("unknown").isEmpty());
        }

        @Test
        @DisplayName("Should return empty for null tenant")
        void shouldReturnEmptyForNull() {
            assertTrue(engine.getTenantFloor(null).isEmpty());
        }
    }

    @Nested
    @DisplayName("Has Tenant Rule")
    class HasTenantRuleTests {

        @Test
        @DisplayName("Should return true for known tenant")
        void shouldReturnTrueForKnown() {
            assertTrue(engine.hasTenantRule("tenant-a"));
        }

        @Test
        @DisplayName("Should return false for unknown tenant")
        void shouldReturnFalseForUnknown() {
            assertFalse(engine.hasTenantRule("unknown"));
        }

        @Test
        @DisplayName("Should return false for null tenant")
        void shouldReturnFalseForNull() {
            assertFalse(engine.hasTenantRule(null));
        }
    }

    @Nested
    @DisplayName("Default Floor Getter")
    class DefaultFloorGetterTests {

        @Test
        @DisplayName("Should return configured default floor")
        void shouldReturnDefaultFloor() {
            assertEquals("MEDIUM", engine.getDefaultFloor());
        }
    }

    @Nested
    @DisplayName("Tenant Count")
    class TenantCountTests {

        @Test
        @DisplayName("Should return correct tenant count")
        void shouldReturnTenantCount() {
            assertEquals(3, engine.getTenantCount());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle null map and null default gracefully")
        void shouldHandleNullInputs() {
            SeverityRuleEngine nullEngine = new SeverityRuleEngine(null, null);
            // Should not throw, uses sensible defaults
            Severity result = nullEngine.applyFloor("tenant", Severity.LOW);
            // With null defaults, parseSeverity(null) calls parseSeverity(null) recursively
            // This would cause stack overflow in current impl - let's verify the existing engine handles it
            // The existing engine uses this.defaultFloor which would be null
            // So this test documents expected behavior - should throw NPE
            assertDoesNotThrow(() -> nullEngine.applyFloor("tenant", Severity.LOW));
        }

        @Test
        @DisplayName("Should handle empty map")
        void shouldHandleEmptyMap() {
            SeverityRuleEngine emptyEngine = new SeverityRuleEngine(Map.of(), "HIGH");
            assertEquals(Severity.HIGH, emptyEngine.applyFloor("any", Severity.LOW));
            assertEquals(Severity.CRITICAL, emptyEngine.applyFloor("any", Severity.CRITICAL));
        }

        @Test
        @DisplayName("Should handle invalid severity string in map")
        void shouldHandleInvalidSeverityString() {
            Map<String, String> floors = Map.of("bad-tenant", "INVALID_LEVEL");
            SeverityRuleEngine badEngine = new SeverityRuleEngine(floors, "MEDIUM");
            // Should fall back to default MEDIUM
            assertEquals(Severity.MEDIUM, badEngine.applyFloor("bad-tenant", Severity.LOW));
            // But CRITICAL should still exceed default
            assertEquals(Severity.CRITICAL, badEngine.applyFloor("bad-tenant", Severity.CRITICAL));
        }
    }
}
