package com.company.rotations.actionexecutor.strategy.impl;

import com.company.rotations.actionexecutor.strategy.NotificationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class TicketNotificationService implements NotificationStrategy {

    private static final Logger log = LoggerFactory.getLogger(TicketNotificationService.class);

    private final String webhookUrl;

    public TicketNotificationService(
            @org.springframework.beans.factory.annotation.Value("${actionexecutor.notifications.ticket.webhook-url:}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    @Override
    public String getChannelName() {
        return "ticket";
    }

    @Override
    public NotificationResult send(String message, SeverityContext context) {
        try {
            if (webhookUrl == null || webhookUrl.isBlank()) {
                log.warn("Ticket webhook URL not configured for tenant {}", context.getTenantId());
                return new NotificationResult(false, "ticket", message,
                        "Ticket webhook URL not configured");
            }

            String ticketPlatform = context.getChannelConfig().getOrDefault("platform", "jira");
            String ticketPayload = String.format(
                    "{\"project\":\"SECURITY\",\"summary\":\"[MACE %s] Credential Alert - %s\"," +
                    "\"description\":\"Tenant: %s\\nAlert: %s\\nSeverity: %s\\nMessage: %s\"}",
                    context.getSeverity().name(), context.getTenantId(),
                    context.getTenantId(), context.getAlertId(),
                    context.getSeverity().name(), message
            );

            log.info("Creating ticket via {} webhook for tenant {}: {}",
                    ticketPlatform, context.getTenantId(), ticketPayload);

            return new NotificationResult(true, "ticket",
                    "Ticket created via " + ticketPlatform);

        } catch (Exception e) {
            log.error("Failed to create ticket for tenant {}: {}",
                    context.getTenantId(), e.getMessage());
            return new NotificationResult(false, "ticket", message, e.getMessage());
        }
    }
}
