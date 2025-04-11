package com.message.engine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.engine.manager.EmailConnectionPoolManager;
import com.message.engine.manager.SmsConnectionPoolManager;
import com.notification.common.repository.NotificationConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationConfigCacheEvictionService {

    private final NotificationConfigRepository repository;
    private final EmailConnectionPoolManager poolManager;
    private final SmsConnectionPoolManager smsConnectionPoolManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Mono<Void> handleMessage(String message) {
        return Mono.fromRunnable(() -> {
            try {
                JsonNode jsonNode = objectMapper.readTree(message);
                String configId = jsonNode.get("notificationConfigId").asText();
                poolManager.evict(configId);
                smsConnectionPoolManager.evict(configId);
                log.info("✅ Cache evicted for updated config ID: {}", configId);
            } catch (Exception e) {
                log.error("❌ Error processing eviction message: {}", message, e);
            }
        });
    }
}
