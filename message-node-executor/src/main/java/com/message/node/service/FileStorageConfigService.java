package com.message.node.service;

import com.notification.common.model.FileStorageConfig;
import com.notification.common.repository.FileStorageConfigRepository;
import com.notification.common.utils.EncryptionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class FileStorageConfigService {

    private final FileStorageConfigRepository repository;

    public FileStorageConfigService(FileStorageConfigRepository repository) {
        this.repository = repository;
    }

    public FileStorageConfig save(FileStorageConfig config) {
        boolean exists = repository.existsByFileStorageNameAndIsActive(config.getFileStorageName(), true);

        if (exists) {
            log.error("Duplicate storage config for name={}", config.getFileStorageName());
            throw new IllegalStateException("An active storage config already exists for fileStorageName '" + config.getFileStorageName() + "'");
        }

        config.setProperties(encryptSensitiveFields(config.getProperties()));
        config.setActive(true);
        FileStorageConfig saved = repository.save(config);
        log.info("Saved file storage config with id={}, name={}", saved.getId(), saved.getFileStorageName());
        return saved;
    }

    public FileStorageConfig update(String id, FileStorageConfig updated) {
        FileStorageConfig existing = getById(id);

        updated.setId(id);
        updated.setActive(true);
        updated.setProperties(encryptSensitiveFields(updated.getProperties()));
        return repository.save(updated);
    }

    public FileStorageConfig getById(String id) {
        return repository.findById(id)
                .map(config -> {
                    config.setProperties(decryptSensitiveFields(config.getProperties()));
                    return config;
                })
                .orElseThrow(() -> {
                    log.error("No storage config found for id={}", id);
                    return new IllegalStateException("No storage config found for id: " + id);
                });
    }

    public List<FileStorageConfig> getAll() {
        return repository.findAll().stream()
                .peek(config -> config.setProperties(decryptSensitiveFields(config.getProperties())))
                .toList();
    }

    public void delete(String id) {
        log.warn("Deleting file storage config with id={}", id);
        repository.deleteById(id);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> encryptSensitiveFields(Map<String, Object> props) {
        Map<String, Object> encrypted = new HashMap<>(props);
        if (encrypted.containsKey("accessKey")) {
            encrypted.put("accessKey", EncryptionUtil.encrypt(encrypted.get("accessKey").toString()));
        }
        if (encrypted.containsKey("secretKey")) {
            encrypted.put("secretKey", EncryptionUtil.encrypt(encrypted.get("secretKey").toString()));
        }
        return encrypted;
    }

    private Map<String, Object> decryptSensitiveFields(Map<String, Object> props) {
        Map<String, Object> decrypted = new HashMap<>(props);
        if (decrypted.containsKey("accessKey")) {
            decrypted.put("accessKey", EncryptionUtil.decrypt(decrypted.get("accessKey").toString()));
        }
        if (decrypted.containsKey("secretKey")) {
            decrypted.put("secretKey", EncryptionUtil.decrypt(decrypted.get("secretKey").toString()));
        }
        return decrypted;
    }
}
