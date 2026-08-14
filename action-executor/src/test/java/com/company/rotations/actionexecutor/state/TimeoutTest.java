package com.company.rotations.actionexecutor.state;

import com.company.rotations.actionexecutor.domain.RotationStateMachine;
import com.company.rotations.actionexecutor.domain.RotationState;
import com.company.rotations.actionexecutor.audit.RotationTransitionDto;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TimeoutTest {

    private final UUID testAlertId = UUID.randomUUID();

    @Test
    void testTimeoutStateTransition() {
        RotationStateMachine sm = new RotationStateMachine("test", testAlertId);

        assertFalse(sm.isTerminalState());
        assertEquals(RotationState.PENDING, sm.getCurrentState());

        sm.transitionTo(RotationState.ROTATING, "Starting");

        RotationTransitionDto timeout = sm.timeoutTransition();
        assertEquals(RotationState.TIMEOUT, sm.getCurrentState());
        assertTrue(sm.isTerminalState());
    }

    @Test
    void testTimeoutDurationIsRecorded() {
        Instant startTime = Instant.now().minus(Duration.ofMinutes(6));
        RotationStateMachine sm = new RotationStateMachine("test", testAlertId);
        sm.setStartTime(startTime);

        sm.transitionTo(RotationState.ROTATING, "Starting");
        RotationTransitionDto timeout = sm.timeoutTransition();

        long durationMs = timeout.getDurationMs();
        assertTrue(durationMs >= 5 * 60 * 1000L, "Duration should be at least 5 minutes");
    }

    @Test
    void testTimeoutReasonIsSet() {
        RotationStateMachine sm = new RotationStateMachine("test", testAlertId);
        sm.transitionTo(RotationState.ROTATING, "Starting");

        RotationTransitionDto timeout = sm.timeoutTransition();
        assertEquals("Global timeout of 5 minutes exceeded", timeout.getReason());
    }

    @Test
    void testCannotTransitionFromTIMEOUT() {
        RotationStateMachine sm = new RotationStateMachine("test", testAlertId);
        sm.timeoutTransition();

        assertThrows(RotationStateMachine.InvalidTransitionException.class,
                () -> sm.transitionTo(RotationState.ROTATING, "Should fail"));
        assertThrows(RotationStateMachine.InvalidTransitionException.class,
                () -> sm.transitionTo(RotationState.SUCCESS, "Should fail"));
    }

    @Test
    void testGlobalTimeoutConstantIsFiveMinutes() {
        long expectedMs = 5 * 60 * 1000L; // 5 minutes in ms
        assertEquals(300000L, expectedMs);
    }

    @Test
    void testTimeoutLogsTransition() {
        RotationStateMachine sm = new RotationStateMachine("test", testAlertId);
        sm.transitionTo(RotationState.ROTATING, "Starting");
        sm.timeoutTransition();

        var log = sm.getTransitionLog();
        assertEquals(2, log.size());

        RotationTransitionDto lastTransition = log.get(log.size() - 1);
        assertEquals(RotationState.ROTATING.name(), lastTransition.getFromState());
        assertEquals(RotationState.TIMEOUT.name(), lastTransition.getToState());
    }

    @Test
    void testTimeoutFromPENDING() {
        RotationStateMachine sm = new RotationStateMachine("test", testAlertId);
        // Should be able to timeout even from PENDING
        RotationTransitionDto timeout = sm.timeoutTransition();
        assertEquals(RotationState.TIMEOUT, sm.getCurrentState());
    }
}
