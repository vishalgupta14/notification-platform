package com.message.engine.consumer.eviction;

import com.message.engine.service.FileStorageConfigCacheEvictionService;
import com.message.engine.service.NotificationConfigCacheEvictionService;
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
@ConditionalOnExpression("'${messaging.mode}'=='activemq' or '${messaging.mode}'=='both'")
public class ArtemisEvictionListener {

    private final NotificationConfigCacheEvictionService evictionService;
    private final FileStorageConfigCacheEvictionService fileStorageConfigCacheEvictionService;

    @JmsListener(destination = "${email.cache.eviction}", containerFactory = "topicListenerFactory", subscription = "#{T(java.util.UUID).randomUUID().toString()}")
    public void listenEmailCacheEviction(String message) {
        log.info("[Artemis] [Eviction Queue] Consumed email cache message: {}", message);
        Mono.fromRunnable(() -> evictionService.handleMessage(message))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    @JmsListener(destination = "${storage.cache.eviction}", containerFactory = "topicListenerFactory", subscription = "#{T(java.util.UUID).randomUUID().toString()}")
    public void listenStorageCacheEviction(String message) {
        log.info("[Artemis] [Eviction Queue] Consumed storage cache message: {}", message);
        Mono.fromRunnable(() -> fileStorageConfigCacheEvictionService.handleMessage(message))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }
}
