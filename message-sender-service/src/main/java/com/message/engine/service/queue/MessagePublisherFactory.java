package com.message.engine.service.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessagePublisherFactory {

    private final ApplicationContext context;

    public MessagePublisher getPublisher(String provider) {
        try {
            return (MessagePublisher) context.getBean(provider.toLowerCase());
        } catch (Exception e) {
            throw new RuntimeException("❌ No MessagePublisher found for provider: " + provider, e);
        }
    }
}
