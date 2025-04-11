package com.message.engine.consumer.notification;

import com.message.engine.service.notification.PushNotificationSendService;
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
                " and '${push.enabled}'=='true'"
)
public class PushNotificationArtemisListener {

    private final PushNotificationSendService pushNotificationSendService;

    @JmsListener(destination = "${push.queue.name}", containerFactory = "queueListenerFactory")
    public void listenPushQueue(String message) {
        log.info("[Artemis] 📳 Consumed Push Notification message: {}", message);

        Mono.fromCallable(() -> JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class))
                .flatMap(pushNotificationSendService::sendPush)
                .doOnError(e -> log.error("❌ Failed to process push message", e))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }
}
