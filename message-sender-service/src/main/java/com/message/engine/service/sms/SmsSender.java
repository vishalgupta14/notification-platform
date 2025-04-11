package com.message.engine.service.sms;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface SmsSender {
    Mono<Void> sendSms(Map<String, Object> config, String to, String message);
}
