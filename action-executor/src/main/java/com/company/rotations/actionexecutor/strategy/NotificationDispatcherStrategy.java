package com.company.rotations.actionexecutor.strategy;

import com.company.rotations.actionexecutor.strategy.impl.*;
import com.company.rotations.models.Severidad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class NotificationDispatcherStrategy {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcherStrategy.class);
    private static final int NOTIFICATION_TIMEOUT_SECONDS = 30;

    private final SlackNotificationService slackService;
    private final EmailNotificationService emailService;
    private final TicketNotificationService ticketService;
    private final AwsSnsNotificationService snsService;
    private final ExecutorService executor;

    private final Map<String, NotificationStrategy> strategyMap;

    public NotificationDispatcherStrategy(SlackNotificationService slackService,
                                          EmailNotificationService emailService,
                                          TicketNotificationService ticketService,
                                          AwsSnsNotificationService snsService) {
        this.slackService = slackService;
        this.emailService = emailService;
        this.ticketService = ticketService;
        this.snsService = snsService;
        this.executor = Executors.newCachedThreadPool();

        this.strategyMap = new HashMap<>();
        this.strategyMap.put("slack", slackService);
        this.strategyMap.put("email", emailService);
        this.strategyMap.put("ticket", ticketService);
        this.strategyMap.put("sns", snsService);
    }

    public List<NotificationStrategy.NotificationResult> dispatchNotifications(
            String tenantId, UUID alertId, Severidad severity, String credentialId,
            List<String> notificationProfile) {

        if (notificationProfile == null || notificationProfile.isEmpty()) {
            notificationProfile = List.of("slack");
        }

        NotificationStrategy.SeverityContext context = new NotificationStrategy.SeverityContext(
                tenantId, alertId, severity, credentialId, null
        );

        List<NotificationStrategy.NotificationResult> results = new ArrayList<>();

        for (String channel : notificationProfile) {
            NotificationStrategy strategy = strategyMap.get(channel.toLowerCase());
            if (strategy == null) {
                log.warn("Unknown notification channel: {}, skipping", channel);
                results.add(new NotificationStrategy.NotificationResult(
                        false, channel, "Unknown channel: " + channel,
                        "No strategy found for channel: " + channel
                ));
                continue;
            }

            try {
                NotificationStrategy.NotificationResult result = strategy.send(
                        generateNotificationMessage(severity, tenantId, alertId, credentialId),
                        context
                );
                results.add(result);
                log.info("Notification result for channel {}: {}", channel, result.isSuccess() ? "SUCCESS" : "FAILED");

            } catch (Exception e) {
                log.error("Failed to send notification via {}: {}", channel, e.getMessage());
                results.add(new NotificationStrategy.NotificationResult(
                        false, channel, e.getMessage(), e.getMessage()
                ));
            }
        }

        boolean allSuccess = results.stream().allMatch(NotificationStrategy.NotificationResult::isSuccess);
        if (allSuccess) {
            log.info("All notifications sent successfully for tenant {} alert {}", tenantId, alertId);
        } else {
            long failedCount = results.stream().filter(r -> !r.isSuccess()).count();
            log.warn("{} notifications failed for tenant {} alert {}",
                    failedCount, tenantId, alertId);
        }

        return results;
    }

    public List<NotificationStrategy.NotificationResult> dispatchNotificationsAsync(
            String tenantId, UUID alertId, Severidad severity, String credentialId,
            List<String> notificationProfile) {

        if (notificationProfile == null || notificationProfile.isEmpty()) {
            notificationProfile = List.of("slack");
        }

        NotificationStrategy.SeverityContext context = new NotificationStrategy.SeverityContext(
                tenantId, alertId, severity, credentialId, null
        );

        List<CompletableFuture<NotificationStrategy.NotificationResult>> futures = new ArrayList<>();

        for (String channel : notificationProfile) {
            NotificationStrategy strategy = strategyMap.get(channel.toLowerCase());
            if (strategy == null) {
                futures.add(CompletableFuture.completedFuture(
                        new NotificationStrategy.NotificationResult(
                                false, channel, "Unknown channel",
                                "No strategy found for channel: " + channel
                        )
                ));
                continue;
            }

            CompletableFuture<NotificationStrategy.NotificationResult> future =
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            return strategy.send(
                                    generateNotificationMessage(severity, tenantId, alertId, credentialId),
                                    context
                            );
                        } catch (Exception e) {
                            return new NotificationStrategy.NotificationResult(
                                    false, channel, e.getMessage(), e.getMessage()
                            );
                        }
                    }, executor);

            futures.add(future);
        }

        CompletableFuture<List<NotificationStrategy.NotificationResult>> allFutures =
                CompletableFuture.allOf(
                        futures.toArray(new CompletableFuture[0])
                ).thenApply(v ->
                        futures.stream()
                                .map(CompletableFuture::join)
                                .collect(Collectors.toList())
                );

        try {
            return allFutures.get(NOTIFICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Notification dispatch timed out for tenant {}: {}", tenantId, e.getMessage());
            futures.forEach(f -> f.cancel(true));
            return resultsFromPartial(futures);
        }
    }

    private List<NotificationStrategy.NotificationResult> resultsFromPartial(
            List<CompletableFuture<NotificationStrategy.NotificationResult>> futures) {
        return futures.stream()
                .filter(f -> !f.isCancelled())
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    private String generateNotificationMessage(Severidad severity, String tenantId,
                                               UUID alertId, String credentialId) {
        return String.format(
                "MACE Alert - Severity: %s%nTenant: %s%nAlert: %s%nCredential: %s%nAction: Credential exposure detected. Rotation process initiated.",
                severity.name(), tenantId, alertId, credentialId
        );
    }
}
