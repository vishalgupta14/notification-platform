package com.message.engine.service.voice;

import java.util.Map;

public interface VoiceSender {
    void sendVoice(Map<String, Object> config, String to, String twiml);
}
