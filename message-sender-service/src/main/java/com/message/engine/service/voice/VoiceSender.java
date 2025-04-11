package com.message.engine.service.voice;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface VoiceSender {
    Mono<Void> sendVoice(Map<String, Object> config, String to, String twiml);
}
