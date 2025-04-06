package com.message.engine.consumer.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.engine.service.email.EmailSendService;
import com.message.engine.service.FileStorageConfigCacheEvictionService;
import com.message.engine.service.NotificationConfigCacheEvictionService;
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
                " and '${email.enabled}'=='true'"
)
public class KafkaMessageListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessageListener.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    @Qualifier("taskExecutor")
    private ThreadPoolTaskExecutor emailTaskExecutor;

    @Autowired
    private EmailSendService emailSendService;

    @Autowired
    private NotificationConfigCacheEvictionService evictionService;

    @Autowired
    private FileStorageConfigCacheEvictionService fileStorageConfigCacheEvictionService;

    @KafkaListener(topics = "${email.queue.name}", groupId = "email-consumer-group")
    public void listenEmailQueue(String message) throws JsonProcessingException {
        log.info("[Kafka] Consumed message: {}", message);
        emailTaskExecutor.submit(() -> {
            try {
                NotificationPayloadDTO request = JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class);
                emailSendService.sendEmail(request);
            } catch (Exception e) {
                log.error("❌ Failed to process email message", e);
            }
        });
    }

    @KafkaListener(
            topics = "${email.cache.eviction}",
            groupId = "#{T(java.util.UUID).randomUUID().toString()}"
    )
    public void listenEmailCacheEviction(String message) {
        log.info("[Kafka] [Eviction Queue] Consumed message: {}", message);
        evictionService.handleMessage(message);
    }

    @KafkaListener(
            topics = "${storage.cache.eviction}",
            groupId = "#{T(java.util.UUID).randomUUID().toString()}"
    )
    public void listenStorageCacheEviction(String message) {
        log.info("[Kafka] [Eviction Queue] Consumed message: {}", message);
        fileStorageConfigCacheEvictionService.handleMessage(message);
    }
}
