package com.notification.common.dto;

import com.notification.common.model.NotificationConfig;
import com.notification.common.model.TemplateEntity;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class NotificationPayloadDTO {
    private String to;
    private List<String> cc;
    private List<String> bcc;
    private String subject;
    private Map<String, Object> customParams;

    private NotificationConfig snapshotConfig;
    private TemplateEntity snapshotTemplate;
}