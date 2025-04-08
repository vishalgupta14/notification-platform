package com.message.engine.service.webhook;

import java.util.Map;

public interface WebhookSender {
    void sendWebhook(Map<String, Object> config, String to, String messageBody);
}
