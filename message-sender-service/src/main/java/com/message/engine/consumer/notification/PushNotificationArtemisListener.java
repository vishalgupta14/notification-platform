package com.message.engine.consumer.notification;

import com.message.engine.service.notification.PushNotificationSendService;
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
        "'${messaging.mode}'=='activemq' or '${messaging.mode}'=='both'" +
        " and '${push.enabled}'=='true'"
)
public class PushNotificationArtemisListener {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationArtemisListener.class);

    private final ThreadPoolTaskExecutor taskExecutor;
    private final PushNotificationSendService pushNotificationSendService;

    public PushNotificationArtemisListener(
            @Qualifier("taskExecutor") ThreadPoolTaskExecutor taskExecutor,
            PushNotificationSendService pushNotificationSendService) {
        this.taskExecutor = taskExecutor;
        this.pushNotificationSendService = pushNotificationSendService;
    }

    @JmsListener(destination = "${push.queue.name}", containerFactory = "queueListenerFactory")
    public void listenPushQueue(String message) {
        log.info("[Artemis] 📳 Consumed Push Notification message: {}", message);

        taskExecutor.submit(() -> {
            try {
                NotificationPayloadDTO request = JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class);
                pushNotificationSendService.sendPush(request);
            } catch (Exception e) {
                log.error("❌ Failed to process push message", e);
            }
        });
    }
}
