package com.message.engine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.engine.manager.EmailConnectionPoolManager;
import com.message.engine.manager.SmsConnectionPoolManager;
import com.notification.common.repository.NotificationConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationConfigCacheEvictionService {

    private static final Logger log = LoggerFactory.getLogger(NotificationConfigCacheEvictionService.class);

    private final NotificationConfigRepository repository;
    private final EmailConnectionPoolManager poolManager;

    private final SmsConnectionPoolManager smsConnectionPoolManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NotificationConfigCacheEvictionService(NotificationConfigRepository repository,
                                                  EmailConnectionPoolManager poolManager, SmsConnectionPoolManager smsConnectionPoolManager) {
        this.repository = repository;
        this.poolManager = poolManager;
        this.smsConnectionPoolManager = smsConnectionPoolManager;
    }

    public void handleMessage(String message) {
        try {
            JsonNode jsonNode = objectMapper.readTree(message);
            String configId = jsonNode.get("notificationConfigId").asText();
            poolManager.evict(configId);
            smsConnectionPoolManager.evict(configId);
            log.info("✅ Cache evicted for updated config ID: {}", configId);
        } catch (Exception e) {
            log.error("❌ Error processing eviction message: {}", message, e);
        }
    }
}
