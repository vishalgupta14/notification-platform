package com.message.engine.consumer.voice;

import com.message.engine.service.voice.VoiceSendService;
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
        " and '${voice.enabled}'=='true'"
)
public class VoiceArtemisMessageListener {

    private static final Logger log = LoggerFactory.getLogger(VoiceArtemisMessageListener.class);

    private final ThreadPoolTaskExecutor voiceTaskExecutor;
    private final VoiceSendService voiceSendService;

    public VoiceArtemisMessageListener(
            @Qualifier("taskExecutor") ThreadPoolTaskExecutor voiceTaskExecutor,
            VoiceSendService voiceSendService) {
        this.voiceTaskExecutor = voiceTaskExecutor;
        this.voiceSendService = voiceSendService;
    }

    @JmsListener(destination = "${voice.queue.name}", containerFactory = "queueListenerFactory")
    public void listenVoiceQueue(String message) {
        log.info("[Artemis] Consumed voice message: {}", message);

        voiceTaskExecutor.submit(() -> {
            try {
                NotificationPayloadDTO request = JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class);
                voiceSendService.sendVoice(request);
            } catch (Exception e) {
                log.error("❌ Failed to process voice message", e);
            }
        });
    }
}
