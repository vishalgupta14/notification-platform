package com.message.engine.service.webhook;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface WebhookSender {
    Mono<Void> sendWebhook(Map<String, Object> config, String to, String messageBody);
}
