package com.message.engine.consumer.sms;

import com.message.engine.service.sms.SmsSendService;
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
                " and '${sms.enabled}'=='true'"
)
public class SmsArtemisMessageListener {

    private final SmsSendService smsSendService;

    @JmsListener(destination = "${sms.queue.name}", containerFactory = "queueListenerFactory")
    public void listenSmsQueue(String message) {
        log.info("[Artemis] Consumed SMS message: {}", message);

        Mono.fromCallable(() -> JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class))
                .flatMap(smsSendService::sendSms)
                .doOnSuccess(unused -> log.info("✅ SMS message sent via Artemis"))
                .doOnError(e -> log.error("❌ Failed to process SMS message via Artemis", e))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }
}
