package com.company.rotations.actionexecutor.domain;

import java.time.Instant;
import java.util.UUID;

public class RotationResult {

    private UUID rotationId;
    private UUID alertId;
    private boolean success;
    private String message;
    private Instant startTime;
    private Instant endTime;
    private int attempts;
    private String newKeyId;
    private String errorMessage;

    public RotationResult() {}

    public RotationResult(UUID alertId, boolean success, String message) {
        this.alertId = alertId;
        this.success = success;
        this.message = message;
        this.startTime = Instant.now();
        this.endTime = Instant.now();
    }

    public UUID getRotationId() { return rotationId; }
    public void setRotationId(UUID rotationId) { this.rotationId = rotationId; }

    public UUID getAlertId() { return alertId; }
    public void setAlertId(UUID alertId) { this.alertId = alertId; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public String getNewKeyId() { return newKeyId; }
    public void setNewKeyId(String newKeyId) { this.newKeyId = newKeyId; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public long getDurationMs() {
        if (startTime == null || endTime == null) return 0;
        return java.time.Duration.between(startTime, endTime).toMillis();
    }
}
