package com.company.rotations.actionexecutor.notification;

import com.company.rotations.actionexecutor.strategy.NotificationStrategy;
import com.company.rotations.actionexecutor.strategy.impl.AwsSnsNotificationService;
import com.company.rotations.models.Severidad;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AwsSnsNotificationServiceTest {

    private final UUID testAlertId = UUID.randomUUID();

    @Mock
    private SnsClient snsClient;

    @Test
    void getChannelName_returnsSns() {
        AwsSnsNotificationService service = new AwsSnsNotificationService(snsClient);
        assertEquals("sns", service.getChannelName());
    }

    @Test
    void send_success_withDefaultTopic() {
        PublishResponse mockResponse = mock(PublishResponse.class);
        when(mockResponse.messageId()).thenReturn("msg-123");
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(mockResponse);

        AwsSnsNotificationService service = new AwsSnsNotificationService(snsClient);

        NotificationStrategy.SeverityContext context = new NotificationStrategy.SeverityContext(
                "tenant1", testAlertId, Severidad.ALTO,
                "AKIA1234", Map.of()
        );

        NotificationStrategy.NotificationResult result = service.send(
                "Credential rotation required", context
        );

        assertTrue(result.isSuccess());
        assertEquals("sns", result.getChannel());
        assertEquals("SNS notification published to arn:aws:sns:us-east-1:default:mace-alerts", result.getMessage());
    }

    @Test
    void send_success_withCustomTopic() {
        PublishResponse mockResponse = mock(PublishResponse.class);
        when(mockResponse.messageId()).thenReturn("msg-456");
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(mockResponse);

        AwsSnsNotificationService service = new AwsSnsNotificationService(snsClient);

        NotificationStrategy.SeverityContext context = new NotificationStrategy.SeverityContext(
                "tenant2", testAlertId, Severidad.CRITICO,
                "AKIA5678", Map.of("topic-arn", "arn:aws:sns:us-west-2:123456789:mace-alerts")
        );

        NotificationStrategy.NotificationResult result = service.send(
                "Rotate credentials now", context
        );

        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("arn:aws:sns:us-west-2:123456789:mace-alerts"));
    }

    @Test
    void send_success_messageStructureJson() {
        PublishResponse mockResponse = mock(PublishResponse.class);
        when(mockResponse.messageId()).thenReturn("msg-789");
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(mockResponse);

        AwsSnsNotificationService service = new AwsSnsNotificationService(snsClient);

        NotificationStrategy.SeverityContext context = new NotificationStrategy.SeverityContext(
                "tenant3", testAlertId, Severidad.BAJO,
                "AKIA9012", Map.of()
        );

        service.send("Low severity alert", context);
    }

    @Test
    void send_success_subjectFormat() {
        PublishResponse mockResponse = mock(PublishResponse.class);
        when(mockResponse.messageId()).thenReturn("msg-subject");
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(mockResponse);

        AwsSnsNotificationService service = new AwsSnsNotificationService(snsClient);

        NotificationStrategy.SeverityContext context = new NotificationStrategy.SeverityContext(
                "mytenant", testAlertId, Severidad.MEDIA,
                "AKIA3456", Map.of()
        );

        service.send("Medium severity", context);
    }

    @Test
    void send_success_messageFormat() {
        PublishResponse mockResponse = mock(PublishResponse.class);
        when(mockResponse.messageId()).thenReturn("msg-msg");
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(mockResponse);

        AwsSnsNotificationService service = new AwsSnsNotificationService(snsClient);

        NotificationStrategy.SeverityContext context = new NotificationStrategy.SeverityContext(
                "tenant-abc", testAlertId, Severidad.ALTO,
                "AKIA7890", Map.of()
        );

        service.send("Custom message text", context);
    }

    @Test
    void send_failure_withException() {
        when(snsClient.publish(any(PublishRequest.class))).thenThrow(
                new RuntimeException("Connection refused")
        );

        AwsSnsNotificationService service = new AwsSnsNotificationService(snsClient);

        NotificationStrategy.SeverityContext context = new NotificationStrategy.SeverityContext(
                "tenant1", testAlertId, Severidad.CRITICO,
                "AKIA1234", Map.of()
        );

        NotificationStrategy.NotificationResult result = service.send(
                "Failed notification", context
        );

        assertFalse(result.isSuccess());
        assertEquals("sns", result.getChannel());
        assertEquals("Connection refused", result.getErrorMessage());
    }

    @Test
    void send_failure_withSdkException() {
        when(snsClient.publish(any(PublishRequest.class))).thenThrow(
                new RuntimeException("InvalidTopicArn: The topic does not exist")
        );

        AwsSnsNotificationService service = new AwsSnsNotificationService(snsClient);

        NotificationStrategy.SeverityContext context = new NotificationStrategy.SeverityContext(
                "tenant2", testAlertId, Severidad.ALTO,
                "AKIA5678", Map.of()
        );

        NotificationStrategy.NotificationResult result = service.send(
                "Another failure", context
        );

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("InvalidTopicArn"));
    }
}
