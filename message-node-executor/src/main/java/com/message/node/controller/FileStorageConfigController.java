package com.message.node.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.common.model.FileStorageConfig;
import com.message.node.producer.MessageProducer;
import com.message.node.service.FileStorageConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/storage-config")
public class FileStorageConfigController {

    private static final Logger log = LoggerFactory.getLogger(FileStorageConfigController.class);

    ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private FileStorageConfigService configService;

    @Autowired
    private MessageProducer messageProducer;

    @Value("${storage.cache.eviction}")
    private String storageCacheEvictionQueueName;

    @PostMapping
    public ResponseEntity<FileStorageConfig> create(@RequestBody FileStorageConfig config) {
        log.info("Creating new file storage config");
        return ResponseEntity.ok(configService.save(config));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileStorageConfig> getById(@PathVariable String id) {
        log.info("Retrieving file storage config with ID: {}", id);
        return ResponseEntity.ok(configService.getById(id));
    }

    @GetMapping("/all")
    public ResponseEntity<List<FileStorageConfig>> getAll() {
        log.info("Retrieving all file storage configs");
        return ResponseEntity.ok(configService.getAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<FileStorageConfig> update(@PathVariable String id, @RequestBody FileStorageConfig updated) throws JsonProcessingException {
        log.info("Updating file storage config with ID: {}", id);
        FileStorageConfig update = configService.update(id, updated);
        Map<String, String> payloadMap = new HashMap<>();
        payloadMap.put("fileStorageConfigId", id);
        String messagePayload = objectMapper.writeValueAsString(payloadMap);
        messageProducer.sendMessage(storageCacheEvictionQueueName,messagePayload,true);
        return ResponseEntity.ok(update);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) throws JsonProcessingException {
        log.info("Deleting file storage config with ID: {}", id);
        configService.delete(id);
        Map<String, String> payloadMap = new HashMap<>();
        payloadMap.put("fileStorageConfigId", id);
        String messagePayload = objectMapper.writeValueAsString(payloadMap);
        messageProducer.sendMessage(storageCacheEvictionQueueName,messagePayload,true);
        return ResponseEntity.ok("Deleted successfully");
    }
}