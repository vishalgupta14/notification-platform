package com.message.engine.consumer.queue;

import com.message.engine.service.queue.NotificationRouterService;
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
                " and '${queue.enabled}'=='true'"
)
public class GenericKafkaPublishQueueListener {

    private final NotificationRouterService notificationRouterService;

    @KafkaListener(topics = "${publish.queue.name}", groupId = "notification-router-group")
    public void listenPublishQueue(String message) {
        log.info("[Kafka] Consumed message from publish queue: {}", message);

        Mono.fromCallable(() -> JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class))
                .doOnNext(notificationRouterService::route)
                .doOnError(e -> log.error("❌ Failed to process message from Kafka publish queue", e))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }
}
