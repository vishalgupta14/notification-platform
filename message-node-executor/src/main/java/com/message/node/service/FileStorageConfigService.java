package com.message.node.service;

import com.notification.common.model.FileStorageConfig;
import com.notification.common.repository.FileStorageConfigRepository;
import com.notification.common.utils.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageConfigService {

    private final FileStorageConfigRepository repository;

    public Mono<FileStorageConfig> save(FileStorageConfig config) {
        return repository.existsByFileStorageNameAndIsActive(config.getFileStorageName(), true)
                .flatMap(exists -> {
                    if (exists) {
                        log.error("Duplicate storage config for name={}", config.getFileStorageName());
                        return Mono.error(new IllegalStateException(
                                "An active storage config already exists for fileStorageName '" + config.getFileStorageName() + "'"
                        ));
                    }

                    config.setProperties(encryptSensitiveFields(config.getProperties()));
                    config.setActive(true);

                    return repository.save(config)
                            .doOnSuccess(saved ->
                                    log.info("Saved file storage config with id={}, name={}", saved.getId(), saved.getFileStorageName()));
                });
    }

    public Mono<FileStorageConfig> update(String id, FileStorageConfig updated) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalStateException("No storage config found for id: " + id)))
                .flatMap(existing -> {
                    updated.setId(id);
                    updated.setActive(true);
                    updated.setProperties(encryptSensitiveFields(updated.getProperties()));
                    return repository.save(updated);
                });
    }

    public Mono<FileStorageConfig> getById(String id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalStateException("No storage config found for id: " + id)))
                .map(config -> {
                    config.setProperties(decryptSensitiveFields(config.getProperties()));
                    return config;
                });
    }

    public Flux<FileStorageConfig> getAll() {
        return repository.findAll()
                .map(config -> {
                    config.setProperties(decryptSensitiveFields(config.getProperties()));
                    return config;
                });
    }

    public Mono<Void> delete(String id) {
        return repository.deleteById(id)
                .doOnSubscribe(sub -> log.warn("Deleting file storage config with id={}", id));
    }

    private Map<String, Object> encryptSensitiveFields(Map<String, Object> props) {
        if (props == null) return new HashMap<>();
        Map<String, Object> encrypted = new HashMap<>(props);
        encrypted.computeIfPresent("accessKey", (k, v) -> EncryptionUtil.encrypt(v.toString()));
        encrypted.computeIfPresent("secretKey", (k, v) -> EncryptionUtil.encrypt(v.toString()));
        return encrypted;
    }

    private Map<String, Object> decryptSensitiveFields(Map<String, Object> props) {
        if (props == null) return new HashMap<>();
        Map<String, Object> decrypted = new HashMap<>(props);
        decrypted.computeIfPresent("accessKey", (k, v) -> EncryptionUtil.decrypt(v.toString()));
        decrypted.computeIfPresent("secretKey", (k, v) -> EncryptionUtil.decrypt(v.toString()));
        return decrypted;
    }
}
