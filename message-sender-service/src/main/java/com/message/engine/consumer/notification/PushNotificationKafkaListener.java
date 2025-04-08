package com.message.engine.consumer.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.engine.service.notification.PushNotificationSendService;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.utils.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression(
        "'${messaging.mode}'=='kafka' or '${messaging.mode}'=='both'" +
        " and '${push.enabled}'=='true'"
)
public class PushNotificationKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationKafkaListener.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ThreadPoolTaskExecutor pushTaskExecutor;
    private final PushNotificationSendService pushNotificationSendService;

    public PushNotificationKafkaListener(
            @Qualifier("taskExecutor") ThreadPoolTaskExecutor pushTaskExecutor,
            PushNotificationSendService pushNotificationSendService) {
        this.pushTaskExecutor = pushTaskExecutor;
        this.pushNotificationSendService = pushNotificationSendService;
    }

    @KafkaListener(topics = "${push.queue.name}", groupId = "push-consumer-group")
    public void listenPushQueue(String message) {
        log.info("[Kafka] 📳 Consumed Push Notification message: {}", message);

        pushTaskExecutor.submit(() -> {
            try {
                NotificationPayloadDTO request = JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class);
                pushNotificationSendService.sendPush(request);
            } catch (Exception e) {
                log.error("❌ Failed to process push message", e);
            }
        });
    }
}
