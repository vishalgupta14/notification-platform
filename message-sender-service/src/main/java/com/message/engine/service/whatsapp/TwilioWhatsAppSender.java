package com.message.engine.service.whatsapp;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Map;

@Component("twilio-whatsapp")
public class TwilioWhatsAppSender implements WhatsAppSender {

    @Override
    public Mono<Void> sendWhatsApp(Map<String, Object> config, String to, String message, List<File> attachments) {
        return Mono.fromRunnable(() -> {
            try {
                Twilio.init(config.get("accountSid").toString(), config.get("authToken").toString());

                String from = "whatsapp:" + config.get("from");
                String toNumber = "whatsapp:" + to;

                if (attachments == null || attachments.isEmpty()) {
                    Message.creator(
                            new PhoneNumber(toNumber),
                            new PhoneNumber(from),
                            message
                    ).create();
                } else {
                    for (File file : attachments) {
                        URI mediaUri = file.toURI().toURL().toURI();
                        Message.creator(
                                new PhoneNumber(toNumber),
                                new PhoneNumber(from),
                                message
                        ).setMediaUrl(List.of(mediaUri)).create();
                    }
                }

            } catch (Exception e) {
                throw new RuntimeException("❌ Failed to send WhatsApp message: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then(); // Reactive, non-blocking
    }
}
