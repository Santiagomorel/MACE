package com.company.rotations.actionexecutor.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RotationTransitionDtoTest {

    private final UUID testAlertId = UUID.randomUUID();
    private final Instant now = Instant.now();

    @Test
    void defaultConstructor() {
        RotationTransitionDto dto = new RotationTransitionDto();
        assertNull(dto.getAlertId());
        assertNull(dto.getFromState());
        assertNull(dto.getToState());
        assertNull(dto.getTimestamp());
        assertEquals(0, dto.getDurationMs());
        assertNull(dto.getReason());
        assertNull(dto.getAttemptNumber());
        assertNull(dto.getErrorMessage());
    }

    @Test
    void fullConstructor() {
        RotationTransitionDto dto = new RotationTransitionDto(
                testAlertId, "PENDING", "ROTATING",
                now, 5000L, "Starting rotation",
                1, null
        );

        assertEquals(testAlertId, dto.getAlertId());
        assertEquals("PENDING", dto.getFromState());
        assertEquals("ROTATING", dto.getToState());
        assertEquals(now, dto.getTimestamp());
        assertEquals(5000L, dto.getDurationMs());
        assertEquals("Starting rotation", dto.getReason());
        assertEquals(Integer.valueOf(1), dto.getAttemptNumber());
        assertNull(dto.getErrorMessage());
    }

    @Test
    void constructor_withErrorMessage() {
        RotationTransitionDto dto = new RotationTransitionDto(
                testAlertId, "ROTATING", "FAIL",
                now, 10000L, "AWS error",
                2, "Connection timeout"
        );

        assertEquals("Connection timeout", dto.getErrorMessage());
        assertEquals(Integer.valueOf(2), dto.getAttemptNumber());
    }

    @Test
    void setters() {
        UUID newId = UUID.randomUUID();

        RotationTransitionDto dto = new RotationTransitionDto();

        dto.setTransitionId(newId);
        assertEquals(newId, dto.getTransitionId());

        dto.setAlertId(testAlertId);
        assertEquals(testAlertId, dto.getAlertId());

        dto.setFromState("PENDING");
        assertEquals("PENDING", dto.getFromState());

        dto.setToState("ROTATING");
        assertEquals("ROTATING", dto.getToState());

        dto.setTimestamp(now);
        assertEquals(now, dto.getTimestamp());

        dto.setDurationMs(3000L);
        assertEquals(3000L, dto.getDurationMs());

        dto.setReason("Test transition");
        assertEquals("Test transition", dto.getReason());

        dto.setAttemptNumber(5);
        assertEquals(Integer.valueOf(5), dto.getAttemptNumber());

        dto.setErrorMessage("Error occurred");
        assertEquals("Error occurred", dto.getErrorMessage());
    }

    @Test
    void transitionFromPendingToRotating() {
        RotationTransitionDto dto = new RotationTransitionDto(
                testAlertId, "PENDING", "ROTATING",
                now, 0L, "Rotation started",
                1, null
        );

        assertEquals("PENDING", dto.getFromState());
        assertEquals("ROTATING", dto.getToState());
    }

    @Test
    void transitionFromRotatingToSuccess() {
        RotationTransitionDto dto = new RotationTransitionDto(
                testAlertId, "ROTATING", "SUCCESS",
                now, 30000L, "Rotation completed",
                1, null
        );

        assertEquals("ROTATING", dto.getFromState());
        assertEquals("SUCCESS", dto.getToState());
        assertEquals(30000L, dto.getDurationMs());
    }

    @Test
    void transitionFromRotatingToFail() {
        RotationTransitionDto dto = new RotationTransitionDto(
                testAlertId, "ROTATING", "FAIL",
                now, 10000L, "Rotation failed",
                2, "AWS error"
        );

        assertEquals("ROTATING", dto.getFromState());
        assertEquals("FAIL", dto.getToState());
        assertEquals("AWS error", dto.getErrorMessage());
    }

    @Test
    void transitionFromFailToRotating() {
        RotationTransitionDto dto = new RotationTransitionDto(
                testAlertId, "FAIL", "ROTATING",
                now, 0L, "Retry",
                2, null
        );

        assertEquals("FAIL", dto.getFromState());
        assertEquals("ROTATING", dto.getToState());
        assertEquals(Integer.valueOf(2), dto.getAttemptNumber());
    }

    @Test
    void durationMsCanBeNull() {
        RotationTransitionDto dto = new RotationTransitionDto();
        assertEquals(0, dto.getDurationMs());
    }

    @Test
    void attemptNumberCanBeNull() {
        RotationTransitionDto dto = new RotationTransitionDto(
                testAlertId, "PENDING", "ROTATING",
                now, 0L, "Start",
                null, null
        );

        assertNull(dto.getAttemptNumber());
    }
}
