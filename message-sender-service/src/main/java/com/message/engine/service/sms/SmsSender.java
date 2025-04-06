package com.message.engine.service.sms;

import java.util.Map;

public interface SmsSender {
    void sendSms(Map<String, Object> config, String to, String message);
}
