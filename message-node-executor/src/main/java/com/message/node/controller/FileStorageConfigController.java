package com.message.node.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.common.model.FileStorageConfig;
import com.message.node.producer.MessageProducer;
import com.message.node.service.FileStorageConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/storage-config")
@RequiredArgsConstructor
public class FileStorageConfigController {

    private final FileStorageConfigService configService;
    private final MessageProducer messageProducer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${storage.cache.eviction}")
    private String storageCacheEvictionQueueName;

    @PostMapping
    public Mono<FileStorageConfig> create(@RequestBody FileStorageConfig config) {
        log.info("Creating new file storage config");
        return configService.save(config);
    }

    @GetMapping("/{id}")
    public Mono<FileStorageConfig> getById(@PathVariable String id) {
        log.info("Retrieving file storage config with ID: {}", id);
        return configService.getById(id);
    }

    @GetMapping("/all")
    public Flux<FileStorageConfig> getAll() {
        log.info("Retrieving all file storage configs");
        return configService.getAll();
    }

    @PutMapping("/{id}")
    public Mono<FileStorageConfig> update(@PathVariable String id, @RequestBody FileStorageConfig updated) {
        log.info("Updating file storage config with ID: {}", id);
        return configService.update(id, updated)
                .doOnSuccess(updatedConfig -> {
                    try {
                        Map<String, String> payloadMap = new HashMap<>();
                        payloadMap.put("fileStorageConfigId", id);
                        String messagePayload = objectMapper.writeValueAsString(payloadMap);
                        messageProducer.sendMessage(storageCacheEvictionQueueName, messagePayload, true);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to serialize message for eviction", e);
                    }
                });
    }

    @DeleteMapping("/{id}")
    public Mono<String> delete(@PathVariable String id) {
        log.info("Deleting file storage config with ID: {}", id);
        return configService.delete(id)
                .then(Mono.fromRunnable(() -> {
                    try {
                        Map<String, String> payloadMap = new HashMap<>();
                        payloadMap.put("fileStorageConfigId", id);
                        String messagePayload = objectMapper.writeValueAsString(payloadMap);
                        messageProducer.sendMessage(storageCacheEvictionQueueName, messagePayload, true);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to serialize message for eviction", e);
                    }
                }))
                .thenReturn("Deleted successfully");
    }
}
