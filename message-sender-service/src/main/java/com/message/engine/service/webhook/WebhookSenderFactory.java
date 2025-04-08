package com.message.engine.service.webhook;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebhookSenderFactory {

    private final ApplicationContext context;

    public WebhookSender getSender(String provider) {
        try {
            return (WebhookSender) context.getBean(provider.toLowerCase());
        } catch (BeansException e) {
            throw new IllegalArgumentException("No webhook sender found for provider: " + provider, e);
        }
    }
}
