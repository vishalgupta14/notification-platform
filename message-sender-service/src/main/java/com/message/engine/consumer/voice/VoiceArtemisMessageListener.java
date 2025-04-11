package com.message.engine.consumer.voice;

import com.message.engine.service.voice.VoiceSendService;
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
                " and '${voice.enabled}'=='true'"
)
public class VoiceArtemisMessageListener {

    private final VoiceSendService voiceSendService;

    @JmsListener(destination = "${voice.queue.name}", containerFactory = "queueListenerFactory")
    public void listenVoiceQueue(String message) {
        log.info("[Artemis] Consumed voice message: {}", message);

        Mono.fromCallable(() -> JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class))
                .flatMap(voiceSendService::sendVoice)
                .doOnSuccess(unused -> log.info("✅ Voice message sent via Artemis"))
                .doOnError(e -> log.error("❌ Failed to process voice message via Artemis", e))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }
}
