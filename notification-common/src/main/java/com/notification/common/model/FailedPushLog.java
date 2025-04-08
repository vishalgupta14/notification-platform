package com.notification.common.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "failed_push_log")
public class FailedPushLog {

    @Id
    private String id;

    private String fcmToken;
    private String message;
    private String notificationConfigId;
    private String templateId;
    private String errorMessage;
    private long timestamp;
}
