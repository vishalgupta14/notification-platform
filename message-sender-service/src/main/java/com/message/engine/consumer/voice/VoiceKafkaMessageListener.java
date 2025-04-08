package com.message.engine.consumer.voice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.engine.service.voice.VoiceSendService;
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
        " and '${voice.enabled}'=='true'"
)
public class VoiceKafkaMessageListener {

    private static final Logger log = LoggerFactory.getLogger(VoiceKafkaMessageListener.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    @Qualifier("taskExecutor")
    private ThreadPoolTaskExecutor voiceTaskExecutor;

    @Autowired
    private VoiceSendService voiceSendService;

    @KafkaListener(topics = "${voice.queue.name}", groupId = "voice-consumer-group")
    public void listenVoiceQueue(String message) throws JsonProcessingException {
        log.info("[Kafka] Consumed voice message: {}", message);
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
