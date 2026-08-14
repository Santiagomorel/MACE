package com.company.rotations.actionexecutor.state;

import com.company.rotations.actionexecutor.domain.RotationStateMachine;
import com.company.rotations.actionexecutor.domain.RotationState;
import com.company.rotations.actionexecutor.audit.RotationTransitionDto;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RotationStateMachineTest {

    private final UUID testAlertId = UUID.randomUUID();

    @Test
    void testSuccessfulTransitionPENDINGtoROTATINGtoSUCCESS() {
        RotationStateMachine sm = new RotationStateMachine("test-rotation", testAlertId);

        assertEquals(RotationState.PENDING, sm.getCurrentState());

        RotationTransitionDto t1 = sm.transitionTo(RotationState.ROTATING, "Starting rotation");
        assertEquals(RotationState.ROTATING, sm.getCurrentState());
        assertEquals("PENDING", t1.getFromState());
        assertEquals("ROTATING", t1.getToState());

        RotationTransitionDto t2 = sm.transitionTo(RotationState.SUCCESS, "Rotation completed");
        assertEquals(RotationState.SUCCESS, sm.getCurrentState());
        assertEquals("ROTATING", t2.getFromState());
        assertEquals("SUCCESS", t2.getToState());

        assertFalse(sm.isTerminalState());
        assertEquals(2, sm.getTransitionLog().size());
    }

    @Test
    void testInvalidTransitionFails() {
        RotationStateMachine sm = new RotationStateMachine("test-rotation", testAlertId);

        assertThrows(RotationStateMachine.InvalidTransitionException.class,
                () -> sm.transitionTo(RotationState.FAIL, "Invalid"));
        assertThrows(RotationStateMachine.InvalidTransitionException.class,
                () -> sm.transitionTo(RotationState.ESCALATE, "Invalid"));

        assertEquals(RotationState.PENDING, sm.getCurrentState());
    }

    @Test
    void testValidTransitionsFromROTATING() {
        RotationStateMachine sm = new RotationStateMachine("test-rotation", testAlertId);
        sm.transitionTo(RotationState.ROTATING, "Start");

        assertTrue(RotationStateMachine.isTransitionValid(RotationState.ROTATING, RotationState.SUCCESS));
        assertTrue(RotationStateMachine.isTransitionValid(RotationState.ROTATING, RotationState.FAIL));
        assertFalse(RotationStateMachine.isTransitionValid(RotationState.ROTATING, RotationState.PENDING));
        assertFalse(RotationStateMachine.isTransitionValid(RotationState.ROTATING, RotationState.ESCALATE));
    }

    @Test
    void testFailToEscalate() {
        RotationStateMachine sm = new RotationStateMachine("test-rotation", testAlertId);
        sm.transitionTo(RotationState.ROTATING, "Start");
        sm.transitionTo(RotationState.FAIL, "Rotation failed");

        // From FAIL we can go back to ROTATING (retry) or ESCALATE
        assertTrue(RotationStateMachine.isTransitionValid(RotationState.FAIL, RotationState.ROTATING));
        assertTrue(RotationStateMachine.isTransitionValid(RotationState.FAIL, RotationState.ESCALATE));

        sm.transitionTo(RotationState.ESCALATE, "Escalating");
        assertTrue(sm.isTerminalState());
    }

    @Test
    void testTimeoutTransition() {
        RotationStateMachine sm = new RotationStateMachine("test-rotation", testAlertId);
        sm.transitionTo(RotationState.ROTATING, "Start");

        RotationTransitionDto timeout = sm.timeoutTransition();
        assertEquals(RotationState.TIMEOUT, sm.getCurrentState());
        assertTrue(sm.isTerminalState());
        assertEquals("Global timeout of 5 minutes exceeded", timeout.getReason());
    }

    @Test
    void testAttemptIncrement() {
        RotationStateMachine sm = new RotationStateMachine("test-rotation", testAlertId);
        sm.transitionTo(RotationState.ROTATING, "Start");

        assertEquals(0, sm.getAttemptCount());
        sm.incrementAttempt();
        assertEquals(1, sm.getAttemptCount());
        sm.incrementAttempt();
        assertEquals(2, sm.getAttemptCount());
    }

    @Test
    void testTransitionLogRecorded() {
        RotationStateMachine sm = new RotationStateMachine("test-rotation", testAlertId);

        sm.transitionTo(RotationState.ROTATING, "Start");
        sm.transitionTo(RotationState.SUCCESS, "Done");

        var log = sm.getTransitionLog();
        assertEquals(2, log.size());
        assertNotNull(log.get(0).getTimestamp());
        assertNotNull(log.get(0).getAlertId());
        assertEquals(testAlertId, log.get(0).getAlertId());
    }

    @Test
    void testTerminalStates() {
        RotationStateMachine sm1 = new RotationStateMachine("test", testAlertId);
        sm1.transitionTo(RotationState.ROTATING, "Starting");
        sm1.transitionTo(RotationState.FAIL, "Failed");
        sm1.transitionTo(RotationState.ESCALATE, "Escalated");
        assertTrue(sm1.isTerminalState());

        RotationStateMachine sm2 = new RotationStateMachine("test", testAlertId);
        sm2.timeoutTransition();
        assertTrue(sm2.isTerminalState());

        RotationStateMachine sm3 = new RotationStateMachine("test", testAlertId);
        assertFalse(sm3.isTerminalState());
    }

    @Test
    void testGetAllowedTransitions() {
        assertEquals(Set.of(RotationState.ROTATING),
                RotationStateMachine.getAllowedTransitions(RotationState.PENDING));
        assertEquals(Set.of(RotationState.SUCCESS, RotationState.FAIL),
                RotationStateMachine.getAllowedTransitions(RotationState.ROTATING));
        assertTrue(RotationStateMachine.getAllowedTransitions(RotationState.ESCALATE).isEmpty());
        assertTrue(RotationStateMachine.getAllowedTransitions(RotationState.TIMEOUT).isEmpty());
    }
}
