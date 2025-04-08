package com.message.engine.service.queue;

import java.util.Map;

public interface MessagePublisher {
    void publish(Map<String, Object> config, String destination, String payloadJson);
}
