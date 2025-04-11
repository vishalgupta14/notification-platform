package com.message.engine.consumer.queue;

import com.message.engine.service.queue.NotificationRouterService;
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
        "('${messaging.mode}'=='activemq' or '${messaging.mode}'=='both') " +
                "and '${queue.enabled}'=='true'"
)
public class GenericPublishQueueListener {

    private final NotificationRouterService notificationRouterService;

    @JmsListener(destination = "${publish.queue.name}", containerFactory = "queueListenerFactory")
    public void listen(String message) {
        log.info("[Artemis] Consumed from publish queue: {}", message);

        Mono.fromCallable(() -> JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class))
                .doOnNext(notificationRouterService::route)
                .doOnError(e -> log.error("❌ Failed to process message from Artemis publish queue", e))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }
}
