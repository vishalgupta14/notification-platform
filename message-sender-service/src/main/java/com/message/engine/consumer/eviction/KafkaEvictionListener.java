package com.message.engine.consumer.eviction;

import com.message.engine.service.FileStorageConfigCacheEvictionService;
import com.message.engine.service.NotificationConfigCacheEvictionService;
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
@ConditionalOnExpression("'${messaging.mode}'=='kafka' or '${messaging.mode}'=='both'")
public class KafkaEvictionListener {

    private final NotificationConfigCacheEvictionService evictionService;
    private final FileStorageConfigCacheEvictionService fileStorageConfigCacheEvictionService;

    @KafkaListener(
            topics = "${email.cache.eviction}",
            groupId = "#{T(java.util.UUID).randomUUID().toString()}"
    )
    public void listenEmailCacheEviction(String message) {
        log.info("[Kafka] [Eviction Queue] Consumed email eviction: {}", message);
        Mono.fromRunnable(() -> evictionService.handleMessage(message))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    @KafkaListener(
            topics = "${storage.cache.eviction}",
            groupId = "#{T(java.util.UUID).randomUUID().toString()}"
    )
    public void listenStorageCacheEviction(String message) {
        log.info("[Kafka] [Eviction Queue] Consumed storage eviction: {}", message);
        Mono.fromRunnable(() -> fileStorageConfigCacheEvictionService.handleMessage(message))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }
}
