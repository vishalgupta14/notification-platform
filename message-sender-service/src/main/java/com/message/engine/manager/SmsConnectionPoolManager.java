package com.message.engine.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.notification.common.dto.CachedSmsClient;
import com.notification.common.model.NotificationConfig;
import com.notification.common.repository.NotificationConfigRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class SmsConnectionPoolManager {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Cache<String, CachedSmsClient> cache = Caffeine.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .refreshAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(100)
            .build(this::reloadFromMongo);

    private final NotificationConfigRepository notificationConfigRepository;

    public SmsConnectionPoolManager(NotificationConfigRepository repo) {
        this.notificationConfigRepository = repo;
    }

    private CachedSmsClient reloadFromMongo(String configId) {
        NotificationConfig config = notificationConfigRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("SMS Config not found: " + configId));
        return createCachedClient(config);
    }

    private CachedSmsClient createCachedClient(NotificationConfig config) {
        Map<String, Object> cfg = config.getConfig();
        String configHash = hashConfig(cfg);
        return new CachedSmsClient(cfg, configHash);
    }

    private String hashConfig(Map<String, Object> configMap) {
        try {
            String json = objectMapper.writeValueAsString(configMap);
            return DigestUtils.sha256Hex(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash SMS config", e);
        }
    }

    public void evict(String configId) {
        cache.invalidate(configId);
    }

    public Map<String, Object> getSmsConfig(NotificationConfig config) {
        CachedSmsClient cached = cache.getIfPresent(config.getId());

        String newHash = hashConfig(config.getConfig());

        if (cached != null && cached.getConfigHash().equals(newHash)) {
            return cached.getConfig();
        } else {
            CachedSmsClient newClient = createCachedClient(config);
            cache.put(config.getId(), newClient);
            return newClient.getConfig();
        }
    }
}
