package com.company.rotations.actionexecutor.audit;

import java.time.Instant;
import java.util.UUID;

public class RotationTransitionDto {

    private UUID transitionId;
    private UUID alertId;
    private String fromState;
    private String toState;
    private Instant timestamp;
    private long durationMs;
    private String reason;
    private Integer attemptNumber;
    private String errorMessage;

    public RotationTransitionDto() {}

    public RotationTransitionDto(UUID alertId, String fromState, String toState,
                                 Instant timestamp, long durationMs, String reason,
                                 Integer attemptNumber, String errorMessage) {
        this.alertId = alertId;
        this.fromState = fromState;
        this.toState = toState;
        this.timestamp = timestamp;
        this.durationMs = durationMs;
        this.reason = reason;
        this.attemptNumber = attemptNumber;
        this.errorMessage = errorMessage;
    }

    public UUID getTransitionId() { return transitionId; }
    public void setTransitionId(UUID transitionId) { this.transitionId = transitionId; }

    public UUID getAlertId() { return alertId; }
    public void setAlertId(UUID alertId) { this.alertId = alertId; }

    public String getFromState() { return fromState; }
    public void setFromState(String fromState) { this.fromState = fromState; }

    public String getToState() { return toState; }
    public void setToState(String toState) { this.toState = toState; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Integer getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(Integer attemptNumber) { this.attemptNumber = attemptNumber; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
