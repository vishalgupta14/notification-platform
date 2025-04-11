package com.message.engine.consumer.whatsapp;

import com.message.engine.service.whatsapp.WhatsAppSendService;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression(
        "'${messaging.mode}'=='kafka' or '${messaging.mode}'=='both'" +
                " and '${whatsapp.enabled}'=='true'"
)
public class WhatsAppKafkaMessageListener {

    private final WhatsAppSendService whatsAppSendService;

    @KafkaListener(topics = "${whatsapp.queue.name}", groupId = "whatsapp-consumer-group")
    public void listenWhatsAppQueue(String message) {
        log.info("[Kafka] Consumed WhatsApp message: {}", message);

        Mono.fromCallable(() -> JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class))
                .flatMap(whatsAppSendService::sendWhatsApp)
                .doOnSuccess(unused -> log.info("✅ WhatsApp message processed successfully"))
                .doOnError(e -> log.error("❌ WhatsApp processing failed", e))
                .subscribeOn(Schedulers.boundedElastic()) // run on separate thread to avoid blocking Kafka thread
                .subscribe();
    }
}
