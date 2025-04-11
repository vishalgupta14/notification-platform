package com.message.engine.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.message.engine.factory.FileUploaderFactory;
import com.message.engine.service.FileStorageConfigService;
import com.notification.common.dto.CachedStorageClient;
import com.notification.common.model.FileStorageConfig;
import com.notification.common.service.upload.FileUploader;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class FileStorageConnectionPoolManager {

    private final FileStorageConfigService configService;
    private final FileUploaderFactory uploaderFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AsyncCache<String, CachedStorageClient> asyncCache = Caffeine.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .maximumSize(100)
            .buildAsync();

    /**
     * Get a cached or newly created CachedStorageClient reactively.
     */
    public Mono<CachedStorageClient> getClient(FileStorageConfig config) {
        String configId = config.getId();
        String currentHash = hashConfig(config.getProperties());

        return Mono.fromFuture(
                asyncCache.get(configId, (key, executor) ->
                        configService.getById(key)
                                .map(this::buildClient)
                                .subscribeOn(Schedulers.boundedElastic()) // safe for blocking logic
                                .toFuture()
                )
        ).flatMap(cached -> {
            if (cached.getConfigHash().equals(currentHash)) {
                return Mono.just(cached);
            } else {
                CachedStorageClient updated = buildClient(config);
                asyncCache.put(configId, CompletableFuture.completedFuture(updated));
                return Mono.just(updated);
            }
        });
    }

    /**
     * Force eviction of a cached client by ID.
     */
    public void evict(String configId) {
        asyncCache.synchronous().invalidate(configId);
    }

    /**
     * Build a new CachedStorageClient using the given config.
     */
    private CachedStorageClient buildClient(FileStorageConfig config) {
        FileUploader uploader = uploaderFactory.getUploader(config.getType());
        return new CachedStorageClient(uploader, config.getProperties(), hashConfig(config.getProperties()));
    }

    /**
     * Generate a SHA-256 hash from the config map.
     */
    private String hashConfig(Map<String, Object> props) {
        try {
            return DigestUtils.sha256Hex(objectMapper.writeValueAsString(props));
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash config", e);
        }
    }
}
