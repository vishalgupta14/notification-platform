package com.message.engine.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.notification.common.dto.CachedMailSender;
import com.notification.common.model.NotificationConfig;
import com.notification.common.repository.NotificationConfigRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class EmailConnectionPoolManager {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotificationConfigRepository configRepository;

    private final AsyncCache<String, CachedMailSender> asyncCache = Caffeine.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .refreshAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(100)
            .buildAsync();

    public Mono<JavaMailSender> getMailSender(NotificationConfig config) {
        String configId = config.getId();
        String newHash = hashConfig(config.getConfig());

        return Mono.fromFuture(
                asyncCache.get(configId, (key, executor) ->
                        configRepository.findById(key)
                                .switchIfEmpty(Mono.error(new RuntimeException("Config not found: " + key)))
                                .map(this::buildSender)
                                .subscribeOn(Schedulers.boundedElastic())
                                .toFuture()
                )
        ).flatMap(cached -> {
            if (cached.getConfigHash().equals(newHash)) {
                return Mono.just(cached.getMailSender());
            } else {
                CachedMailSender updated = buildSender(config);
                asyncCache.put(configId, CompletableFuture.completedFuture(updated));
                return Mono.just(updated.getMailSender());
            }
        });
    }


    public void evict(String configId) {
        asyncCache.synchronous().invalidate(configId);
    }

    private CachedMailSender buildSender(NotificationConfig config) {
        Map<String, Object> cfg = config.getConfig();

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost((String) cfg.get("host"));
        sender.setPort((Integer) cfg.get("port"));
        sender.setUsername((String) cfg.get("username"));
        sender.setPassword((String) cfg.get("password"));
        sender.getJavaMailProperties().put("mail.smtp.auth", "true");
        sender.getJavaMailProperties().put("mail.smtp.starttls.enable", "true");

        return new CachedMailSender(sender, hashConfig(cfg));
    }

    private String hashConfig(Map<String, Object> configMap) {
        try {
            return DigestUtils.sha256Hex(objectMapper.writeValueAsString(configMap));
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash email config", e);
        }
    }
}
