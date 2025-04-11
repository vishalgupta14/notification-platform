package com.message.engine.consumer.webhook;

import com.message.engine.service.webhook.WebhookSendService;
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
                " and '${webhook.enabled}'=='true'"
)
public class WebhookKafkaMessageListener {

    private final WebhookSendService webhookSendService;

    @KafkaListener(topics = "${webhook.queue.name}", groupId = "webhook-consumer-group")
    public void listenWebhookQueue(String message) {
        log.info("[Kafka] Consumed Webhook message: {}", message);

        Mono.fromCallable(() -> JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class))
                .flatMap(webhookSendService::sendWebhook)
                .doOnSuccess(unused -> log.info("✅ Webhook message processed successfully via Kafka"))
                .doOnError(e -> log.error("❌ Failed to process Webhook message via Kafka", e))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }
}
