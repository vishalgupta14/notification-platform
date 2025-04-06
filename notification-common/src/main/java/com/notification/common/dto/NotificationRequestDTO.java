package com.notification.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class NotificationRequestDTO {

    @NotBlank(message = "NotificationConfigId must not be blank")
    private String notificationConfigId;

    @NotBlank(message = "TemplateId must not be blank")
    private String templateId;

    @NotEmpty(message = "To emails must not be empty")
    private String to;

    private List<@Email String> cc;
    private List<@Email String> bcc;
    private String emailSubject;
    private Map<String, Object> customParams;

    private String scheduleCron;
    private boolean scheduled;

}

