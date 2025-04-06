package com.notification.common.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Document(collection = "notification_config")
public class NotificationConfig {

    @Id
    private String id;
    private String clientName;
    private String channel;
    private String provider;

    private Map<String, Object> config;

    private boolean isActive;

    private String fallbackConfigId;
    private Map<String, Object> privacyFallbackConfig;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
