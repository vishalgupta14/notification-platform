package com.notification.common.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "scheduled_notifications")
@CompoundIndex(name = "status_active_idx", def = "{'status': 1, 'active': 1}")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ScheduledNotification {
    @Id
    private String id;

    private NotificationConfig notificationConfig;
    private TemplateEntity template;
    private String to;
    private List<String> cc;
    private List<String> bcc;
    private String emailSubject;
    private Map<String, Object> customParams;
    private String queueName;
    private String scheduleCron;
    private String timeZone;
    private boolean active;

    private String status; // NEW, PICKED, COMPLETED, FAILED
    private String pickedBy;
    private LocalDateTime pickedAt;
    private int retryCount;
}
