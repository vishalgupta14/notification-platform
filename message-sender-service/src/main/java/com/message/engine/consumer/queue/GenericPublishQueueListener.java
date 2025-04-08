package com.message.engine.consumer.queue;

import com.message.engine.service.queue.NotificationRouterService;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.utils.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression(
        "('${messaging.mode}'=='activemq' or '${messaging.mode}'=='both') " +
        "and '${queue.enabled}'=='true'"
)
public class GenericPublishQueueListener {

    private static final Logger log = LoggerFactory.getLogger(GenericPublishQueueListener.class);

    private final ThreadPoolTaskExecutor taskExecutor;
    private final NotificationRouterService notificationRouterService;

    public GenericPublishQueueListener(@Qualifier("taskExecutor") ThreadPoolTaskExecutor taskExecutor,
                                       NotificationRouterService notificationRouterService) {
        this.taskExecutor = taskExecutor;
        this.notificationRouterService = notificationRouterService;
    }

    @JmsListener(destination = "${publish.queue.name}", containerFactory = "queueListenerFactory")
    public void listen(String message) {
        log.info("[Artemis] Consumed from publish queue: {}", message);

        taskExecutor.submit(() -> {
            try {
                NotificationPayloadDTO payload = JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class);
                notificationRouterService.route(payload);
            } catch (Exception e) {
                log.error("❌ Failed to process publish queue message", e);
            }
        });
    }
}
