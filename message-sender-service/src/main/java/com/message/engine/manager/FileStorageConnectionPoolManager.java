package com.message.engine.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.message.engine.factory.FileUploaderFactory;
import com.notification.common.model.FileStorageConfig;
import com.message.engine.service.FileStorageConfigService;
import com.notification.common.dto.CachedStorageClient;
import com.notification.common.service.upload.FileUploader;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class FileStorageConnectionPoolManager {

    private final FileStorageConfigService configService;
    private final FileUploaderFactory uploaderFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Cache<String, CachedStorageClient> cache = Caffeine.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .maximumSize(100)
            .build(this::loadClient);

    public FileStorageConnectionPoolManager(FileStorageConfigService configService,
                                            FileUploaderFactory uploaderFactory) {
        this.configService = configService;
        this.uploaderFactory = uploaderFactory;
    }

    private CachedStorageClient loadClient(String configId) {
        FileStorageConfig config = configService.getById(configId);
        FileUploader uploader = uploaderFactory.getUploader(config.getType());
        String hash = hashConfig(config.getProperties());

        return new CachedStorageClient(uploader, config.getProperties(), hash);
    }

    private String hashConfig(Map<String, Object> properties) {
        try {
            return DigestUtils.sha256Hex(objectMapper.writeValueAsString(properties));
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash file storage config", e);
        }
    }

    public void evict(String configId) {
        cache.invalidate(configId);
    }

    public CachedStorageClient getClient(FileStorageConfig config) {
        CachedStorageClient cached = cache.getIfPresent(config.getId());

        String newHash = hashConfig(config.getProperties());
        if (cached != null && cached.getConfigHash().equals(newHash)) {
            return cached;
        }

        // Recreate and cache
        FileUploader uploader = uploaderFactory.getUploader(config.getType());
        CachedStorageClient newClient = new CachedStorageClient(uploader, config.getProperties(), newHash);
        cache.put(config.getId(), newClient);
        return newClient;
    }
}
