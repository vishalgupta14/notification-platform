package com.message.engine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.engine.manager.FileStorageConnectionPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FileStorageConfigCacheEvictionService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageConfigCacheEvictionService.class);

    private final FileStorageConnectionPoolManager poolManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FileStorageConfigCacheEvictionService(FileStorageConnectionPoolManager poolManager) {
        this.poolManager = poolManager;
    }

    public void handleMessage(String message) {
        try {
            JsonNode jsonNode = objectMapper.readTree(message);
            String configId = jsonNode.get("fileStorageConfigId").asText();

            poolManager.evict(configId);
            log.info("✅ File storage cache evicted for config ID: {}", configId);
        } catch (Exception e) {
            log.error("❌ Error processing file storage eviction message: {}", message, e);
        }
    }
}
