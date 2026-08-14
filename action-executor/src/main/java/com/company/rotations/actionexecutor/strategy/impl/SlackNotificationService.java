package com.company.rotations.actionexecutor.strategy.impl;

import com.company.rotations.actionexecutor.strategy.NotificationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SlackNotificationService implements NotificationStrategy {

    private static final Logger log = LoggerFactory.getLogger(SlackNotificationService.class);

    private final String webhookUrl;

    public SlackNotificationService(
            @org.springframework.beans.factory.annotation.Value("${actionexecutor.notifications.slack.webhook-url:}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    @Override
    public String getChannelName() {
        return "slack";
    }

    @Override
    public NotificationResult send(String message, SeverityContext context) {
        try {
            if (webhookUrl == null || webhookUrl.isBlank()) {
                log.warn("Slack webhook URL not configured, skipping notification for tenant {}",
                        context.getTenantId());
                return new NotificationResult(false, "slack", message, "Slack webhook URL not configured");
            }

            String channel = context.getChannelConfig().getOrDefault("channel", "#incidentes");
            String color = switch (context.getSeverity()) {
                case CRITICO -> "danger";
                case ALTO -> "warning";
                case MEDIA -> "#ffa500";
                case BAJO -> "good";
            };

            String slackPayload = String.format(
                    "{\"channel\":\"%s\",\"attachments\":[{\"color\":\"%s\",\"title\":\"MACE Alert - %s\"," +
                    "\"text\":\"%s\",\"fields\":[{\"title\":\"Tenant\",\"value\":\"%s\",\"short\":true}," +
                    "{\"title\":\"Severity\",\"value\":\"%s\",\"short\":true}," +
                    "{\"title\":\"Alert\",\"value\":\"%s\",\"short\":true}]}]}",
                    channel, color, context.getSeverity(),
                    message.replace("\"", "\\\""),
                    context.getTenantId(),
                    context.getSeverity().name(),
                    context.getAlertId()
            );

            log.info("Sending Slack notification to {} for tenant {}: {}",
                    channel, context.getTenantId(), slackPayload);

            return new NotificationResult(true, "slack", "Notification sent to " + channel);

        } catch (Exception e) {
            log.error("Failed to send Slack notification for tenant {}: {}",
                    context.getTenantId(), e.getMessage());
            return new NotificationResult(false, "slack", message, e.getMessage());
        }
    }
}
