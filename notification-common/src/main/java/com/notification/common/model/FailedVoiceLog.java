package com.notification.common.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Builder
@Document(collection = "failed_voice_logs")
public class FailedVoiceLog {

    @Id
    private String id;

    private String phoneNumber;
    private String message;
    private String notificationConfigId;
    private String templateId;

    private String errorMessage;
    private long timestamp;
}
