package com.notification.common.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "failed_email_logs")
public class FailedEmailLog {
    @Id
    private String id;
    private String toEmail;
    private List<String> cc;
    private List<String> bcc;
    private String subject;
    private String htmlContent;
    private String templateId;
    private String notificationConfigId;
    private String errorMessage;
    private Long timestamp;
}
