package com.notification.common.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "failed_sms_logs")
public class FailedSmsLog {

    @Id
    private String id;

    private String phoneNumber;
    private String message;
    private String templateId;
    private String notificationConfigId;
    private String errorMessage;
    private Long timestamp;
}
