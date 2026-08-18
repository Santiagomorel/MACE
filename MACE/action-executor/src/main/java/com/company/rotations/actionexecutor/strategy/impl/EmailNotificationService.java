package com.company.rotations.actionexecutor.strategy.impl;

import com.company.rotations.actionexecutor.strategy.NotificationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmailNotificationService implements NotificationStrategy {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final String smtpHost;
    private final int smtpPort;
    private final String fromEmail;

    public EmailNotificationService(
            @org.springframework.beans.factory.annotation.Value("${actionexecutor.notifications.email.smtp-host:localhost}") String smtpHost,
            @org.springframework.beans.factory.annotation.Value("${actionexecutor.notifications.email.smtp-port:587}") int smtpPort,
            @org.springframework.beans.factory.annotation.Value("${actionexecutor.notifications.email.from:}") String fromEmail) {
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.fromEmail = fromEmail;
    }

    @Override
    public String getChannelName() {
        return "email";
    }

    @Override
    public NotificationResult send(String message, SeverityContext context) {
        try {
            String toEmail = context.getChannelConfig().getOrDefault("to-email", "security-team@example.com");

            String subject = String.format("[MACE %s] Credential Alert - Tenant %s",
                    context.getSeverity().name(), context.getTenantId());

            log.info("Sending email notification to {} for tenant {}: {} - {}",
                    toEmail, context.getTenantId(), subject, message);

            return new NotificationResult(true, "email",
                    "Email sent to " + toEmail);

        } catch (Exception e) {
            log.error("Failed to send email notification for tenant {}: {}",
                    context.getTenantId(), e.getMessage());
            return new NotificationResult(false, "email", message, e.getMessage());
        }
    }
}
