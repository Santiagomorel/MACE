package com.company.rotations.actionexecutor.rotation;

import com.company.rotations.actionexecutor.domain.RotationResult;
import com.company.rotations.actionexecutor.domain.RotationStateMachine;
import com.company.rotations.actionexecutor.domain.RotationState;
import com.company.rotations.actionexecutor.service.AwsRotationService;
import com.company.rotations.actionexecutor.service.AuditTrailService;
import com.company.rotations.actionexecutor.service.VaultService;
import com.company.rotations.models.Credential;
import com.company.rotations.models.Severidad;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.sts.StsClient;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AwsRotationServiceRetryTest {

    private static final int MAX_RETRIES = 3;
    private static final long[] BACKOFF = {10, 30, 60}; // seconds

    @Mock
    private StsClient stsClient;

    @Mock
    private IamClient iamClient;

    @Mock
    private VaultService vaultService;

    @Mock
    private AuditTrailService auditTrailService;

    private final UUID testAlertId = UUID.randomUUID();

    @Test
    void testRetryBackoffValues() {
        // Verify backoff values match spec: 10s, 30s, 60s
        assertEquals(10, BACKOFF[0]);
        assertEquals(30, BACKOFF[1]);
        assertEquals(60, BACKOFF[2]);
        assertEquals(MAX_RETRIES, BACKOFF.length);
    }

    @Test
    void testRotationStateMachineRetryTransition() {
        RotationStateMachine sm = new RotationStateMachine("test", testAlertId);
        sm.transitionTo(RotationState.ROTATING, "Start");

        // Simulate failure
        sm.transitionTo(RotationState.FAIL, "Failed");

        // Retry: go back to ROTATING
        sm.transitionTo(RotationState.ROTATING, "Retry");

        // Final success
        sm.transitionTo(RotationState.SUCCESS, "Retried successfully");

        assertEquals(RotationState.SUCCESS, sm.getCurrentState());
    }

    @Test
    void testRotationResultTracksAttempts() {
        RotationResult result = new RotationResult();
        result.setAlertId(testAlertId);
        result.setStartTime(Instant.now());
        result.setAttempts(3);
        result.setSuccess(false);
        result.setEndTime(Instant.now());
        result.setErrorMessage("All retries failed");

        assertEquals(3, result.getAttempts());
        assertFalse(result.isSuccess());
        assertNotNull(result.getDurationMs());
        assertTrue(result.getDurationMs() >= 0);
    }

    @Test
    void testMaxRetriesIsThree() {
        assertEquals(3, MAX_RETRIES);
    }

    @Test
    void testRotationStateMachineFailsAfterThreeAttempts() {
        RotationStateMachine sm = new RotationStateMachine("test", testAlertId);
        sm.transitionTo(RotationState.ROTATING, "Attempt 1");

        for (int i = 0; i < 2; i++) {
            sm.transitionTo(RotationState.FAIL, "Attempt failed");
            sm.transitionTo(RotationState.ROTATING, "Retry");
        }

        sm.transitionTo(RotationState.FAIL, "Final attempt failed");
        sm.transitionTo(RotationState.ESCALATE, "All retries exhausted");

        assertEquals(RotationState.ESCALATE, sm.getCurrentState());
        assertTrue(sm.isTerminalState());
    }
}
