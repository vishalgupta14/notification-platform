package com.message.engine.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.notification.common.model.NotificationConfig;
import com.notification.common.repository.NotificationConfigRepository;
import com.notification.common.dto.CachedMailSender;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class EmailConnectionPoolManager {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Cache<String, CachedMailSender> cache = Caffeine.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)       
            .refreshAfterWrite(30, TimeUnit.MINUTES)       
            .maximumSize(100)                              
            .build(this::reloadFromMongo);                 

    private final NotificationConfigRepository notificationConfigRepository;

    public EmailConnectionPoolManager(NotificationConfigRepository repo) {
        this.notificationConfigRepository = repo;
    }

    
    private CachedMailSender reloadFromMongo(String configId) {
        NotificationConfig config = notificationConfigRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("Config not found: " + configId));
        return createCachedSender(config);
    }

    
    private CachedMailSender createCachedSender(NotificationConfig config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        Map<String, Object> cfg = config.getConfig();

        sender.setHost((String) cfg.get("host"));
        sender.setPort((Integer) cfg.get("port"));
        sender.setUsername((String) cfg.get("username"));
        sender.setPassword((String) cfg.get("password"));
        sender.getJavaMailProperties().put("mail.smtp.auth", "true");
        sender.getJavaMailProperties().put("mail.smtp.starttls.enable", "true");

        String configHash = hashConfig(cfg);
        return new CachedMailSender(sender, configHash);
    }

    
    private String hashConfig(Map<String, Object> configMap) {
        try {
            String json = objectMapper.writeValueAsString(configMap);
            return DigestUtils.sha256Hex(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash config", e);
        }
    }

    
    public void evict(String configId) {
        cache.invalidate(configId);
    }

    public JavaMailSender getMailSender(NotificationConfig config) {
        CachedMailSender cached = cache.getIfPresent(config.getId());

        String newHash = hashConfig(config.getConfig());

        if (cached != null && cached.getConfigHash().equals(newHash)) {
            return cached.getMailSender(); 
        } else {
            CachedMailSender newSender = createCachedSender(config);
            cache.put(config.getId(), newSender);
            return newSender.getMailSender();
        }
    }
}
