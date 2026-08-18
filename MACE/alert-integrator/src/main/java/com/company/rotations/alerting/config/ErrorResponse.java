package com.company.rotations.alerting.config;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String path,
    String message,
    List<String> details
) {
    public ErrorResponse(Instant timestamp, int status, String error, String path, String message, List<String> details) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.path = path;
        this.message = message;
        this.details = details != null ? details : List.of();
    }
}
