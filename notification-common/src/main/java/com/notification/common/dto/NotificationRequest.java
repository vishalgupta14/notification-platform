package com.notification.common.dto;

import java.util.Map;
import lombok.Data;

@Data
public class NotificationRequest {
    private String email;
    private String phone;
    private String title;
    private String body;
    private Map<String, String> data;
}
