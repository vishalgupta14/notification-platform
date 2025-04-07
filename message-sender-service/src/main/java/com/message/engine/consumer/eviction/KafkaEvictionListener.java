package com.message.engine.consumer.eviction;

import com.message.engine.service.FileStorageConfigCacheEvictionService;
import com.message.engine.service.NotificationConfigCacheEvictionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${messaging.mode}'=='kafka' or '${messaging.mode}'=='both'")
public class KafkaEvictionListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaEvictionListener.class);

    private final NotificationConfigCacheEvictionService evictionService;
    private final FileStorageConfigCacheEvictionService fileStorageConfigCacheEvictionService;

    public KafkaEvictionListener(
            NotificationConfigCacheEvictionService evictionService,
            FileStorageConfigCacheEvictionService fileStorageConfigCacheEvictionService
    ) {
        this.evictionService = evictionService;
        this.fileStorageConfigCacheEvictionService = fileStorageConfigCacheEvictionService;
    }

    @KafkaListener(
            topics = "${email.cache.eviction}",
            groupId = "#{T(java.util.UUID).randomUUID().toString()}"
    )
    public void listenEmailCacheEviction(String message) {
        log.info("[Kafka] [Eviction Queue] Consumed email eviction: {}", message);
        evictionService.handleMessage(message);
    }

    @KafkaListener(
            topics = "${storage.cache.eviction}",
            groupId = "#{T(java.util.UUID).randomUUID().toString()}"
    )
    public void listenStorageCacheEviction(String message) {
        log.info("[Kafka] [Eviction Queue] Consumed storage eviction: {}", message);
        fileStorageConfigCacheEvictionService.handleMessage(message);
    }
}
