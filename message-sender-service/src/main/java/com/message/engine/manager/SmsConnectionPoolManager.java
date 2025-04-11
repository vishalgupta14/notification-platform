package com.message.engine.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.notification.common.dto.CachedSmsClient;
import com.notification.common.model.NotificationConfig;
import com.notification.common.repository.NotificationConfigRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class SmsConnectionPoolManager {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotificationConfigRepository notificationConfigRepository;

    private final AsyncCache<String, CachedSmsClient> asyncCache = Caffeine.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .maximumSize(100)
            .buildAsync();

    public Mono<Map<String, Object>> getSmsConfig(NotificationConfig config) {
        String configId = config.getId();
        return Mono.fromFuture(
                asyncCache.get(configId, (key, executor) ->
                        notificationConfigRepository.findById(key)
                                .switchIfEmpty(Mono.error(new IllegalArgumentException("Config not found: " + key)))
                                .map(this::createCachedClient)
                                .toFuture()
                )
        ).map(cached -> {
            String currentHash = hashConfig(config.getConfig());
            if (cached.getConfigHash().equals(currentHash)) {
                return cached.getConfig();
            } else {
                CachedSmsClient updated = createCachedClient(config);
                asyncCache.put(configId, CompletableFuture.completedFuture(updated));
                return updated.getConfig();
            }
        });
    }

    public void evict(String configId) {
        asyncCache.synchronous().invalidate(configId);
    }

    private CachedSmsClient createCachedClient(NotificationConfig config) {
        Map<String, Object> cfg = config.getConfig();
        return new CachedSmsClient(cfg, hashConfig(cfg));
    }

    private String hashConfig(Map<String, Object> configMap) {
        try {
            String json = objectMapper.writeValueAsString(configMap);
            return DigestUtils.sha256Hex(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash config", e);
        }
    }
}
