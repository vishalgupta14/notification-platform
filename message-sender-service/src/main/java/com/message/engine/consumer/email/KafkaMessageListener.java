package com.message.engine.consumer.email;

import com.message.engine.service.email.EmailSendService;
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
                " and '${email.enabled}'=='true'"
)
public class KafkaMessageListener {

    private final EmailSendService emailSendService;

    @KafkaListener(topics = "${email.queue.name}", groupId = "email-consumer-group")
    public void listenEmailQueue(String message) {
        log.info("[Kafka] 📧 Consumed email message: {}", message);

        Mono.fromCallable(() -> JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class))
                .flatMap(emailSendService::sendEmail)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("❌ Failed to process email message", e))
                .subscribe();
    }
}
