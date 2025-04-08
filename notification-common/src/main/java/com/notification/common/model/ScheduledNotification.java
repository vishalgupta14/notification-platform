package com.notification.common.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "scheduled_notifications")
public class ScheduledNotification {

    @Id
    private String id;

    private String notificationConfigId;
    private String templateId;
    private String to;
    private List<String> cc;
    private List<String> bcc;
    private String emailSubject;
    private Map<String, Object> customParams;

    private String scheduleCron;
    private boolean active;
}
