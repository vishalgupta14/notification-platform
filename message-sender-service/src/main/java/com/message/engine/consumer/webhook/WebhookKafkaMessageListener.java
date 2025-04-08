package com.message.engine.consumer.webhook;

import com.message.engine.service.webhook.WebhookSendService;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression(
        "'${messaging.mode}'=='kafka' or '${messaging.mode}'=='both'" +
        " and '${webhook.enabled}'=='true'"
)
public class WebhookKafkaMessageListener {

    private static final Logger log = LoggerFactory.getLogger(WebhookKafkaMessageListener.class);

    @Qualifier("taskExecutor")
    private final ThreadPoolTaskExecutor webhookTaskExecutor;

    private final WebhookSendService webhookSendService;

    @KafkaListener(topics = "${webhook.queue.name}", groupId = "webhook-consumer-group")
    public void listenWebhookQueue(String message) {
        log.info("[Kafka] Consumed Webhook message: {}", message);
        webhookTaskExecutor.submit(() -> {
            try {
                NotificationPayloadDTO payload = JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class);
                webhookSendService.sendWebhook(payload);
            } catch (Exception e) {
                log.error("❌ Failed to process Webhook message", e);
            }
        });
    }
}
