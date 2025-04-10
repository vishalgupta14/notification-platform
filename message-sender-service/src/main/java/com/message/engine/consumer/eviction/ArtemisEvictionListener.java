package com.message.engine.consumer.eviction;

import com.message.engine.service.FileStorageConfigCacheEvictionService;
import com.message.engine.service.NotificationConfigCacheEvictionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${messaging.mode}'=='activemq' or '${messaging.mode}'=='both'")
public class ArtemisEvictionListener {

    private static final Logger log = LoggerFactory.getLogger(ArtemisEvictionListener.class);

    private final NotificationConfigCacheEvictionService evictionService;
    private final FileStorageConfigCacheEvictionService fileStorageConfigCacheEvictionService;

    public ArtemisEvictionListener(
            NotificationConfigCacheEvictionService evictionService,
            FileStorageConfigCacheEvictionService fileStorageConfigCacheEvictionService
    ) {
        this.evictionService = evictionService;
        this.fileStorageConfigCacheEvictionService = fileStorageConfigCacheEvictionService;
    }

    @JmsListener(destination = "${email.cache.eviction}", containerFactory = "topicListenerFactory",  subscription = "#{T(java.util.UUID).randomUUID().toString()}")
    public void listenEmailCacheEviction(String message) {
        log.info("[Artemis] [Eviction Queue] Consumed email cache message: {}", message);
        evictionService.handleMessage(message);
    }

    @JmsListener(destination = "${storage.cache.eviction}", containerFactory = "topicListenerFactory",  subscription = "#{T(java.util.UUID).randomUUID().toString()}")
    public void listenStorageCacheEviction(String message) {
        log.info("[Artemis] [Eviction Queue] Consumed storage cache message: {}", message);
        fileStorageConfigCacheEvictionService.handleMessage(message);
    }
}
