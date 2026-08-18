package com.company.rotations.actionexecutor.notification;

import com.company.rotations.actionexecutor.strategy.NotificationStrategy;
import com.company.rotations.actionexecutor.strategy.NotificationDispatcherStrategy;
import com.company.rotations.actionexecutor.strategy.impl.*;
import com.company.rotations.models.Severidad;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    private final UUID testAlertId = UUID.randomUUID();

    @Test
    void testDispatcherChoosesCorrectStrategy() {
        SlackNotificationService slackService = new SlackNotificationService("http://test.webhook");
        EmailNotificationService emailService = new EmailNotificationService("localhost", 587, "test@test.com");
        TicketNotificationService ticketService = new TicketNotificationService("http://test.ticket");
        AwsSnsNotificationService snsService = null;

        NotificationDispatcherStrategy dispatcher = new NotificationDispatcherStrategy(
                slackService, emailService, ticketService, snsService
        );

        List<NotificationStrategy.NotificationResult> results = dispatcher.dispatchNotifications(
                "tenant1", testAlertId, Severidad.CRITICO, "AKIA1234",
                List.of("slack")
        );

        assertEquals(1, results.size());
        assertEquals("slack", results.get(0).getChannel());
    }

    @Test
    void testMultiChannelNotification() {
        SlackNotificationService slackService = new SlackNotificationService("http://test.webhook");
        EmailNotificationService emailService = new EmailNotificationService("localhost", 587, "test@test.com");
        TicketNotificationService ticketService = new TicketNotificationService("http://test.ticket");
        AwsSnsNotificationService snsService = null;

        NotificationDispatcherStrategy dispatcher = new NotificationDispatcherStrategy(
                slackService, emailService, ticketService, snsService
        );

        List<NotificationStrategy.NotificationResult> results = dispatcher.dispatchNotifications(
                "tenant1", testAlertId, Severidad.ALTO, "AKIA1234",
                List.of("slack", "email", "ticket")
        );

        assertEquals(3, results.size());
        assertEquals("slack", results.get(0).getChannel());
        assertEquals("email", results.get(1).getChannel());
        assertEquals("ticket", results.get(2).getChannel());
    }

    @Test
    void testUnknownChannelHandled() {
        SlackNotificationService slackService = new SlackNotificationService("http://test.webhook");
        EmailNotificationService emailService = new EmailNotificationService("localhost", 587, "test@test.com");
        TicketNotificationService ticketService = new TicketNotificationService("http://test.ticket");
        AwsSnsNotificationService snsService = null;

        NotificationDispatcherStrategy dispatcher = new NotificationDispatcherStrategy(
                slackService, emailService, ticketService, snsService
        );

        List<NotificationStrategy.NotificationResult> results = dispatcher.dispatchNotifications(
                "tenant1", testAlertId, Severidad.MEDIA, "AKIA1234",
                List.of("slack", "unknown-channel")
        );

        assertEquals(2, results.size());
        assertEquals("slack", results.get(0).getChannel());
        assertEquals("unknown-channel", results.get(1).getChannel());
        assertFalse(results.get(1).isSuccess());
    }

    @Test
    void testDefaultProfileIsSlack() {
        SlackNotificationService slackService = new SlackNotificationService("http://test.webhook");
        EmailNotificationService emailService = new EmailNotificationService("localhost", 587, "test@test.com");
        TicketNotificationService ticketService = new TicketNotificationService("http://test.ticket");
        AwsSnsNotificationService snsService = null;

        NotificationDispatcherStrategy dispatcher = new NotificationDispatcherStrategy(
                slackService, emailService, ticketService, snsService
        );

        List<NotificationStrategy.NotificationResult> results = dispatcher.dispatchNotifications(
                "tenant1", testAlertId, Severidad.BAJO, "AKIA1234",
                null
        );

        assertEquals(1, results.size());
        assertEquals("slack", results.get(0).getChannel());
    }

    @Test
    void testEmptyProfileIsSlack() {
        SlackNotificationService slackService = new SlackNotificationService("http://test.webhook");
        EmailNotificationService emailService = new EmailNotificationService("localhost", 587, "test@test.com");
        TicketNotificationService ticketService = new TicketNotificationService("http://test.ticket");
        AwsSnsNotificationService snsService = null;

        NotificationDispatcherStrategy dispatcher = new NotificationDispatcherStrategy(
                slackService, emailService, ticketService, snsService
        );

        List<NotificationStrategy.NotificationResult> results = dispatcher.dispatchNotifications(
                "tenant1", testAlertId, Severidad.BAJO, "AKIA1234",
                List.of()
        );

        assertEquals(1, results.size());
        assertEquals("slack", results.get(0).getChannel());
    }
}
