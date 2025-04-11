package com.message.node.service;

import com.notification.common.dto.NotificationConfigDTO;
import com.notification.common.model.NotificationConfig;
import com.notification.common.repository.NotificationConfigRepository;
import com.notification.common.utils.ConfigMaskingUtil;
import com.notification.common.utils.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationConfigService {

    private final NotificationConfigRepository repository;

    public Mono<NotificationConfig> save(NotificationConfig config) {
        return repository.existsByClientNameAndChannelAndIsActive(config.getClientName(), config.getChannel(), true)
                .flatMap(exists -> {
                    if (exists) {
                        log.error("Duplicate config attempted for clientId={}, channel={}", config.getClientName(), config.getChannel());
                        return Mono.error(new IllegalStateException(
                                "An active config already exists for clientId '" + config.getClientName() + "' and channel '" + config.getChannel() + "'"
                        ));
                    }

                    config.setConfig(encryptSensitiveFields(config.getConfig()));
                    config.setCreatedAt(LocalDateTime.now());
                    config.setUpdatedAt(LocalDateTime.now());
                    config.setActive(true);

                    return repository.save(config)
                            .doOnSuccess(saved -> log.info("Saved config with id={}, clientId={}, channel={}",
                                    saved.getId(), saved.getClientName(), saved.getChannel()));
                });
    }

    public Mono<NotificationConfig> getActiveConfig(String clientId, String channel) {
        return repository.findByClientNameAndChannelAndIsActive(clientId, channel, true)
                .map(config -> {
                    config.setConfig(decryptSensitiveFields(config.getConfig()));
                    return config;
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.error("No active config found for clientId={}, channel={}", clientId, channel);
                    return Mono.error(new IllegalStateException("No active config found for clientId '" + clientId + "' and channel '" + channel + "'"));
                }));
    }

    public Mono<NotificationConfig> findById(String id) {
        return repository.findById(id)
                .map(config -> {
                    config.setConfig(decryptSensitiveFields(config.getConfig()));
                    return config;
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.error("No config found for id={}", id);
                    return Mono.error(new IllegalStateException("No config found for id: " + id));
                }));
    }

    public Mono<Void> deleteById(String id) {
        log.warn("Deleting config with id={}", id);
        return repository.deleteById(id);
    }

    public Flux<NotificationConfig> findAll() {
        log.debug("Fetching all configs from repository");
        return repository.findAll()
                .map(config -> {
                    config.setConfig(decryptSensitiveFields(config.getConfig()));
                    return config;
                });
    }

    public NotificationConfigDTO toDTO(NotificationConfig config) {
        NotificationConfigDTO dto = new NotificationConfigDTO();
        dto.setId(config.getId());
        dto.setClientId(config.getClientName());
        dto.setChannel(config.getChannel());
        dto.setProvider(config.getProvider());
        dto.setActive(config.isActive());
        dto.setUpdatedAt(config.getUpdatedAt());
        dto.setConfigSummary(ConfigMaskingUtil.maskConfig(config.getConfig()));
        return dto;
    }

    private Map<String, Object> encryptSensitiveFields(Map<String, Object> config) {
        if (config == null) return new HashMap<>();
        Map<String, Object> encrypted = new HashMap<>(config);
        encrypted.computeIfPresent("password", (k, v) -> EncryptionUtil.encrypt(v.toString()));
        encrypted.computeIfPresent("authToken", (k, v) -> EncryptionUtil.encrypt(v.toString()));
        return encrypted;
    }

    private Map<String, Object> decryptSensitiveFields(Map<String, Object> config) {
        if (config == null) return new HashMap<>();
        Map<String, Object> decrypted = new HashMap<>(config);
        decrypted.computeIfPresent("password", (k, v) -> EncryptionUtil.decrypt(v.toString()));
        decrypted.computeIfPresent("authToken", (k, v) -> EncryptionUtil.decrypt(v.toString()));
        return decrypted;
    }
}
