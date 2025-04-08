package com.message.engine.service.voice;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VoiceSenderFactory {

    private final ApplicationContext context;

    public VoiceSender getSender(String provider) {
        try {
            return (VoiceSender) context.getBean(provider.toLowerCase());
        } catch (BeansException e) {
            throw new IllegalArgumentException("No Voice sender found for provider: " + provider, e);
        }
    }
}
