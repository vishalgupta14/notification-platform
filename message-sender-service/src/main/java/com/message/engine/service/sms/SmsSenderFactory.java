package com.message.engine.service.sms;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmsSenderFactory {

    private final ApplicationContext context;

    public SmsSender getSender(String provider) {
        try {
            return (SmsSender) context.getBean(provider.toLowerCase());
        } catch (BeansException e) {
            throw new IllegalArgumentException("No SMS sender found for provider: " + provider, e);
        }
    }
}
