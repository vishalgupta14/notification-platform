package com.message.engine.consumer.webhook;

import com.message.engine.service.webhook.WebhookSendService;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression(
        "'${messaging.mode}'=='activemq' or '${messaging.mode}'=='both'" +
        " and '${webhook.enabled}'=='true'"
)
public class WebhookArtemisMessageListener {

    private static final Logger log = LoggerFactory.getLogger(WebhookArtemisMessageListener.class);

    @Qualifier("taskExecutor")
    private final ThreadPoolTaskExecutor taskExecutor;

    private final WebhookSendService webhookSendService;

    @JmsListener(destination = "${webhook.queue.name}", containerFactory = "queueListenerFactory")
    public void listenWebhookQueue(String message) {
        log.info("[Artemis] Consumed Webhook message: {}", message);
        taskExecutor.submit(() -> {
            try {
                NotificationPayloadDTO payload = JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class);
                webhookSendService.sendWebhook(payload);
            } catch (Exception e) {
                log.error("❌ Failed to process Webhook message", e);
            }
        });
    }
}
