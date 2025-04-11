package com.message.engine.consumer.email;

import com.message.engine.service.email.EmailSendService;
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
                " and '${email.enabled}'=='true'"
)
public class ArtemisMessageListener {

    private final EmailSendService emailSendService;

    @JmsListener(destination = "${email.queue.name}", containerFactory = "queueListenerFactory")
    public void listenEmailQueue(String message) {
        log.info("[Artemis] 📧 Consumed email message: {}", message);

        Mono.fromCallable(() -> JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class))
                .flatMap(emailSendService::sendEmail)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("❌ Failed to process email message", e))
                .subscribe();
    }
}
