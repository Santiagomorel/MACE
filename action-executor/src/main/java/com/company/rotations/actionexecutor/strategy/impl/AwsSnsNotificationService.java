package com.company.rotations.actionexecutor.strategy.impl;

import com.company.rotations.actionexecutor.strategy.NotificationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.Map;

@Service
public class AwsSnsNotificationService implements NotificationStrategy {

    private static final Logger log = LoggerFactory.getLogger(AwsSnsNotificationService.class);

    private final SnsClient snsClient;

    public AwsSnsNotificationService(SnsClient snsClient) {
        this.snsClient = snsClient;
    }

    @Override
    public String getChannelName() {
        return "sns";
    }

    @Override
    public NotificationResult send(String message, SeverityContext context) {
        try {
            String topicArn = context.getChannelConfig().getOrDefault(
                    "topic-arn",
                    "arn:aws:sns:us-east-1:default:mace-alerts"
            );

            PublishRequest request = PublishRequest.builder()
                    .topicArn(topicArn)
                    .subject(String.format("[MACE %s] Credential Alert - Tenant %s",
                            context.getSeverity().name(), context.getTenantId()))
                    .message(String.format("Tenant: %s%nAlert: %s%nSeverity: %s%nMessage: %s",
                            context.getTenantId(), context.getAlertId(),
                            context.getSeverity().name(), message))
                    .messageStructure("json")
                    .build();

            PublishResponse response = snsClient.publish(request);
            log.info("SNS notification published for tenant {}: messageId={}",
                    context.getTenantId(), response.messageId());

            return new NotificationResult(true, "sns",
                    "SNS notification published to " + topicArn);

        } catch (Exception e) {
            log.error("Failed to send SNS notification for tenant {}: {}",
                    context.getTenantId(), e.getMessage());
            return new NotificationResult(false, "sns", message, e.getMessage());
        }
    }
}
