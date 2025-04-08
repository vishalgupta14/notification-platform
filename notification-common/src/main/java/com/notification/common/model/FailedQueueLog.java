package com.notification.common.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "failed_queue_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedQueueLog {

    @Id
    private String id;

    private String notificationConfigId;
    private String templateId;
    private String queueName;
    private String message;
    private long timestamp;
    private String errorMessage;
}
