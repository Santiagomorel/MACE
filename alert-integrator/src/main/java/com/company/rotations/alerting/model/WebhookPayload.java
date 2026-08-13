package com.company.rotations.alerting.model;

import com.company.rotations.models.GenericAlertModel;

import java.time.Instant;

public record WebhookPayload(
        GenericAlertModel alert,
        String rawBody,
        String source,
        Instant receivedAt
) {}
