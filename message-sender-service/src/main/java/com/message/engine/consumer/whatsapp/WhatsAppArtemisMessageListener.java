package com.message.engine.consumer.whatsapp;

import com.message.engine.service.whatsapp.WhatsAppSendService;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression(
        "'${messaging.mode}'=='activemq' or '${messaging.mode}'=='both'" +
                " and '${whatsapp.enabled}'=='true'"
)
public class WhatsAppArtemisMessageListener {

    private final WhatsAppSendService whatsAppSendService;

    @JmsListener(destination = "${whatsapp.queue.name}", containerFactory = "queueListenerFactory")
    public void listenWhatsAppQueue(String message) {
        log.info("[Artemis] Consumed WhatsApp message: {}", message);

        Mono.fromCallable(() -> JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class))
                .flatMap(whatsAppSendService::sendWhatsApp)
                .doOnSuccess(unused -> log.info("✅ WhatsApp message processed successfully via Artemis"))
                .doOnError(e -> log.error("❌ WhatsApp message processing failed via Artemis", e))
                .subscribeOn(Schedulers.boundedElastic()) // so it doesn't block the JMS thread
                .subscribe();
    }
}
