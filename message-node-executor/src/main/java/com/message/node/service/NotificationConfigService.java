package com.message.node.service;

import com.notification.common.dto.NotificationConfigDTO;
import com.notification.common.model.NotificationConfig;
import com.notification.common.repository.NotificationConfigRepository;
import com.notification.common.utils.ConfigMaskingUtil;
import com.notification.common.utils.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationConfigService {

    private static final Logger log = LoggerFactory.getLogger(NotificationConfigService.class);
    private final NotificationConfigRepository repository;

    public NotificationConfigService(NotificationConfigRepository repository) {
        this.repository = repository;
    }

    public NotificationConfig save(NotificationConfig config) {
        boolean exists = repository.existsByClientNameAndChannelAndIsActive(
                config.getClientName(), config.getChannel(), true
        );

        if (exists) {
            log.error("Duplicate config attempted for clientId={}, channel={}", config.getClientName(), config.getChannel());
            throw new IllegalStateException("An active config already exists for clientId '"
                    + config.getClientName() + "' and channel '" + config.getChannel() + "'");
        }

        config.setConfig(encryptSensitiveFields(config.getConfig()));
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        config.setActive(true);
        NotificationConfig saved = repository.save(config);
        log.info("Saved config with id={}, clientId={}, channel={}", saved.getId(), saved.getClientName(), saved.getChannel());
        return saved;
    }

    public NotificationConfig getActiveConfig(String clientId, String channel) {
        return repository.findByClientNameAndChannelAndIsActive(clientId, channel, true)
                .map(config -> {
                    config.setConfig(decryptSensitiveFields(config.getConfig()));
                    return config;
                })
                .orElseThrow(() -> {
                    log.error("No active config found for clientId={}, channel={}", clientId, channel);
                    return new IllegalStateException("No active config found for clientId '" + clientId + "' and channel '" + channel + "'");
                });
    }

    public NotificationConfig findById(String id) {
        return repository.findById(id)
                .map(config -> {
                    config.setConfig(decryptSensitiveFields(config.getConfig()));
                    return config;
                })
                .orElseThrow(() -> {
                    log.error("No config found for id={}", id);
                    return new IllegalStateException("No config found for id: " + id);
                });
    }

    public void deleteById(String id) {
        log.warn("Deleting config with id={}", id);
        repository.deleteById(id);
    }

    public List<NotificationConfig> findAll() {
        log.debug("Fetching all configs from repository");
        return repository.findAll();
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
        Map<String, Object> encrypted = new HashMap<>(config);
        if (encrypted.containsKey("password")) {
            encrypted.put("password", EncryptionUtil.encrypt(encrypted.get("password").toString()));
        }
        if (encrypted.containsKey("authToken")) {
            encrypted.put("authToken", EncryptionUtil.encrypt(encrypted.get("authToken").toString()));
        }
        return encrypted;
    }

    private Map<String, Object> decryptSensitiveFields(Map<String, Object> config) {
        Map<String, Object> decrypted = new HashMap<>(config);
        if (decrypted.containsKey("password")) {
            decrypted.put("password", EncryptionUtil.decrypt(decrypted.get("password").toString()));
        }
        if (decrypted.containsKey("authToken")) {
            decrypted.put("authToken", EncryptionUtil.decrypt(decrypted.get("authToken").toString()));
        }
        return decrypted;
    }
}
