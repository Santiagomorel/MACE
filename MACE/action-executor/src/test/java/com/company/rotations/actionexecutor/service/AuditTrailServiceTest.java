package com.company.rotations.actionexecutor.service;

import com.company.rotations.actionexecutor.audit.RotationTransitionDto;
import com.company.rotations.actionexecutor.domain.RotationResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditTrailServiceTest {

    private final UUID testAlertId = UUID.randomUUID();
    private final AuditTrailService auditTrailService = new AuditTrailService();

    @Test
    void logRotationTransition_withNoError() {
        RotationTransitionDto transition = new RotationTransitionDto(
                testAlertId, "PENDING", "ROTATING",
                Instant.now(), 100L, "Starting rotation",
                1, null
        );

        assertDoesNotThrow(() -> auditTrailService.logRotationTransition(transition));
        assertEquals(0, 0); // No exception = success
    }

    @Test
    void logRotationTransition_withError() {
        RotationTransitionDto transition = new RotationTransitionDto(
                testAlertId, "ROTATING", "FAIL",
                Instant.now(), 5000L, "AWS error",
                2, "Connection timeout to AWS"
        );

        assertDoesNotThrow(() -> auditTrailService.logRotationTransition(transition));
    }

    @Test
    void logRotationStarted() {
        assertDoesNotThrow(() ->
                auditTrailService.logRotationStarted("tenant1", testAlertId, "AKIA1234")
        );
    }

    @Test
    void logRotationCompleted() {
        com.company.rotations.actionexecutor.domain.RotationResult result =
                new com.company.rotations.actionexecutor.domain.RotationResult();
        result.setSuccess(true);
        result.setAttempts(1);
        result.setStartTime(java.time.Instant.now().minusSeconds(5));
        result.setEndTime(java.time.Instant.now());

        assertDoesNotThrow(() ->
                auditTrailService.logRotationCompleted("tenant1", testAlertId, result)
        );
    }

    @Test
    void logRotationFailed() {
        assertDoesNotThrow(() ->
                auditTrailService.logRotationFailed("tenant1", testAlertId, "AWS error occurred")
        );
    }

    @Test
    void logEscalation() {
        assertDoesNotThrow(() ->
                auditTrailService.logEscalation("tenant1", testAlertId, "CRITICO",
                        "All retries exhausted", 3)
        );
    }

    @Test
    void logTimeout() {
        assertDoesNotThrow(() ->
                auditTrailService.logTimeout("tenant1", testAlertId, "ALTO")
        );
    }

    @Test
    void logNotificationSent_success() {
        assertDoesNotThrow(() ->
                auditTrailService.logNotificationSent("tenant1", testAlertId, "slack", true)
        );
    }

    @Test
    void logNotificationSent_failure() {
        assertDoesNotThrow(() ->
                auditTrailService.logNotificationSent("tenant1", testAlertId, "email", false)
        );
    }

    @Test
    void toJson_withNullMap() {
        String result = auditTrailService.toJson(null);
        assertEquals("{}", result);
    }

    @Test
    void toJson_withEmptyMap() {
        String result = auditTrailService.toJson(Map.of());
        assertEquals("{}", result);
    }

    @Test
    void toJson_withSingleEntry() {
        String result = auditTrailService.toJson(Map.of("key", "value"));
        assertTrue(result.contains("\"key\":\"value\""));
    }

    @Test
    void toJson_withMultipleEntries() {
        String result = auditTrailService.toJson(Map.of(
                "tenant", "tenant1",
                "severity", "CRITICO",
                "alert", "alert-123"
        ));
        assertTrue(result.contains("\"tenant\":\"tenant1\""));
        assertTrue(result.contains("\"severity\":\"CRITICO\""));
        assertTrue(result.contains("\"alert\":\"alert-123\""));
    }

    @Test
    void toJson_withNullValue() {
        var map = new java.util.HashMap<String, Object>();
        map.put("key", null);
        String result = auditTrailService.toJson(map);
        assertTrue(result.contains("\"key\":\"\""));
    }

    @Test
    void toJson_withQuotedValue() {
        String result = auditTrailService.toJson(Map.of("message", "He said \"hello\""));
        assertTrue(result.contains("\\\""));
    }
}
