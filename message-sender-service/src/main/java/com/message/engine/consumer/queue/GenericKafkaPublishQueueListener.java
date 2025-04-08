package com.message.engine.consumer.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.message.engine.service.queue.NotificationRouterService;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.utils.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression(
        "'${messaging.mode}'=='kafka' or '${messaging.mode}'=='both'" +
        " and '${queue.enabled}'=='true'"
)
public class GenericKafkaPublishQueueListener {

    private static final Logger log = LoggerFactory.getLogger(GenericKafkaPublishQueueListener.class);

    @Autowired
    @Qualifier("taskExecutor")
    private ThreadPoolTaskExecutor taskExecutor;

    @Autowired
    private NotificationRouterService notificationRouterService;

    @KafkaListener(topics = "${publish.queue.name}", groupId = "notification-router-group")
    public void listenPublishQueue(String message) throws JsonProcessingException {
        log.info("[Kafka] Consumed message from publish queue: {}", message);
        taskExecutor.submit(() -> {
            try {
                NotificationPayloadDTO payload = JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class);
                notificationRouterService.route(payload);
            } catch (Exception e) {
                log.error("❌ Failed to process message from publish queue", e);
            }
        });
    }
}
