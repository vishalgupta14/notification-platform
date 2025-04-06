package com.notification.common.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@Document(collection = "failed_attachment_logs")
public class FailedAttachmentLog {

    @Id
    private String id;

    private String templateId;
    private String notificationConfigId;
    private String errorMessage;

    private long timestamp;
}
