package com.message.node.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.notification.common.dto.CachedStorageClient;
import com.message.node.factory.FileUploaderFactory;
import com.notification.common.model.FileStorageConfig;
import com.message.node.service.FileStorageConfigService;
import com.notification.common.service.upload.FileUploader;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class FileStorageConnectionPoolManager {

    private final FileStorageConfigService configService;
    private final FileUploaderFactory uploaderFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AsyncCache<String, CachedStorageClient> cache = Caffeine.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .maximumSize(100)
            .buildAsync((configId, executor) -> loadClient(configId).toFuture());

    public FileStorageConnectionPoolManager(FileStorageConfigService configService,
                                            FileUploaderFactory uploaderFactory) {
        this.configService = configService;
        this.uploaderFactory = uploaderFactory;
    }

    private Mono<CachedStorageClient> loadClient(String configId) {
        return configService.getById(configId)
                .map(config -> {
                    FileUploader uploader = uploaderFactory.getUploader(config.getType());
                    String hash = hashConfig(config.getProperties());
                    return new CachedStorageClient(uploader, config.getProperties(), hash);
                });
    }

    private String hashConfig(Map<String, Object> properties) {
        try {
            return DigestUtils.sha256Hex(objectMapper.writeValueAsString(properties));
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash file storage config", e);
        }
    }

    public void evict(String configId) {
        cache.synchronous().invalidate(configId);
    }

    public Mono<CachedStorageClient> getClient(FileStorageConfig config) {
        String configId = config.getId();
        String newHash = hashConfig(config.getProperties());

        return Mono.fromFuture(cache.get(configId, (key, executor) -> loadClient(key).toFuture()))
                .map(cached -> {
                    if (cached.getConfigHash().equals(newHash)) {
                        return cached;
                    }
                    FileUploader uploader = uploaderFactory.getUploader(config.getType());
                    CachedStorageClient updated = new CachedStorageClient(uploader, config.getProperties(), newHash);
                    cache.synchronous().put(configId, updated);
                    return updated;
                });
    }
}
