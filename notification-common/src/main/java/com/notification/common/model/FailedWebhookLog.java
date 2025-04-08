package com.notification.common.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@Document(collection = "failed_webhook_logs")
public class FailedWebhookLog {

    @Id
    private String id;

    private String notificationConfigId;
    private String templateId;
    private String webhookUrl;
    private String message;
    private long timestamp;
    private String errorMessage;
}
