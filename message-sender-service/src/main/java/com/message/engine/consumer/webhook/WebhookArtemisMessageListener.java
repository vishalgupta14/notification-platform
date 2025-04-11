package com.message.engine.consumer.webhook;

import com.message.engine.service.webhook.WebhookSendService;
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
                " and '${webhook.enabled}'=='true'"
)
public class WebhookArtemisMessageListener {

    private final WebhookSendService webhookSendService;

    @JmsListener(destination = "${webhook.queue.name}", containerFactory = "queueListenerFactory")
    public void listenWebhookQueue(String message) {
        log.info("[Artemis] Consumed Webhook message: {}", message);

        Mono.fromCallable(() -> JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class))
                .flatMap(webhookSendService::sendWebhook)
                .doOnSuccess(unused -> log.info("✅ Webhook message processed successfully via Artemis"))
                .doOnError(e -> log.error("❌ Failed to process Webhook message via Artemis", e))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }
}
