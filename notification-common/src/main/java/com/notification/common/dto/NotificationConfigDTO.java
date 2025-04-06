package com.notification.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
public class NotificationConfigDTO {
    private String id;
    private String clientId;
    private String channel;
    private String provider;
    private Map<String, Object> configSummary; // Masked / partial
    private boolean active;
    private LocalDateTime updatedAt;
}
