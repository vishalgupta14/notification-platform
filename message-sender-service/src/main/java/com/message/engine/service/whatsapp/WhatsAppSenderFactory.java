package com.message.engine.service.whatsapp;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WhatsAppSenderFactory {

    private final ApplicationContext context;

    public WhatsAppSender getSender(String provider) {
        try {
            return (WhatsAppSender) context.getBean(provider.toLowerCase());
        } catch (BeansException e) {
            throw new IllegalArgumentException("No WhatsApp sender found for provider: " + provider, e);
        }
    }
}
