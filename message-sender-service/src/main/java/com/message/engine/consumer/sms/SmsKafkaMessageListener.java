package com.message.engine.consumer.sms;

import com.message.engine.service.sms.SmsSendService;
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
                " and '${sms.enabled}'=='true'"
)
public class SmsKafkaMessageListener {

    private final SmsSendService smsSendService;

    @KafkaListener(topics = "${sms.queue.name}", groupId = "sms-consumer-group")
    public void listenSmsQueue(String message) {
        log.info("[Kafka] Consumed SMS message: {}", message);

        Mono.fromCallable(() -> JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class))
                .flatMap(smsSendService::sendSms)
                .doOnSuccess(unused -> log.info("✅ SMS message sent via Kafka"))
                .doOnError(e -> log.error("❌ Failed to process SMS message via Kafka", e))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }
}
