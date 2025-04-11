package com.message.engine.service.whatsapp;

import com.message.engine.manager.FileStorageConnectionPoolManager;
import com.message.engine.service.FailedWhatsappLogService;
import com.message.engine.service.FileStorageConfigService;
import com.notification.common.dto.CachedStorageClient;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.model.*;
import com.notification.common.repository.NotificationConfigRepository;
import com.notification.common.service.upload.FileUploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppSendService {

    private final NotificationConfigRepository configRepository;
    private final FileStorageConfigService fileStorageConfigService;
    private final FileStorageConnectionPoolManager fileStorageConnectionPoolManager;
    private final WhatsAppSenderFactory senderFactory;
    private final FailedWhatsappLogService failedLogService;

    @Value("${notification.whatsapp.enabled:true}")
    private boolean isWhatsAppEnabled;

    public Mono<Void> sendWhatsApp(NotificationPayloadDTO request) {
        if (!isWhatsAppEnabled) {
            log.info("[WHATSAPP-SIMULATION] Sending is DISABLED");
            return Mono.empty();
        }

        NotificationConfig config = request.getSnapshotConfig();
        String to = request.getTo();
        String message = request.getSnapshotTemplate().getContent();
        String configId = config.getId();
        String templateId = request.getSnapshotTemplate().getId();

        return prepareAttachments(request.getSnapshotTemplate())
                .flatMap(attachments -> trySend(config, to, message, attachments)
                        .flatMap(sent -> {
                            if (sent) return Mono.empty();

                            return fallback(config, to, message, attachments)
                                    .flatMap(fallbackSent -> {
                                        if (fallbackSent) return Mono.empty();

                                        return logFailure(configId, templateId, to, message);
                                    });
                        }));
    }

    private Mono<List<File>> prepareAttachments(TemplateEntity template) {
        if (template.getAttachments() == null || template.getAttachments().isEmpty()) {
            return Mono.just(Collections.emptyList());
        }

        return Flux.fromIterable(template.getAttachments())
                .flatMap(ref ->
                        fileStorageConfigService.getById(ref.getFileStorageId())
                                .flatMap(config ->
                                        fileStorageConnectionPoolManager.getClient(config) // returns Mono<CachedStorageClient>
                                                .flatMap(cached -> {
                                                    FileUploader uploader = cached.getUploader(); // safe now
                                                    String key = extractKey(ref.getFileUrl());

                                                    return Mono.fromCallable(() -> {
                                                        File file = File.createTempFile("wa-", key);
                                                        try (InputStream in = uploader.downloadFile(key, cached.getProperties());
                                                             FileOutputStream out = new FileOutputStream(file)) {
                                                            in.transferTo(out);
                                                        }
                                                        return file;
                                                    }).subscribeOn(Schedulers.boundedElastic());
                                                })
                                )
                                .onErrorResume(e -> {
                                    log.warn("⚠️ Failed to prepare one attachment (ref={}): {}", ref.getFileUrl(), e.getMessage());
                                    return Mono.empty();
                                })
                )
                .collectList();
    }


    private Mono<Boolean> trySend(NotificationConfig config, String to, String message, List<File> attachments) {
        return Mono.fromCallable(() -> {
            Map<String, Object> resolvedConfig = config.getConfig();
            WhatsAppSender sender = senderFactory.getSender(config.getProvider() + "-whatsapp");
            sender.sendWhatsApp(resolvedConfig, to, message, attachments);
            log.info("✅ WhatsApp sent to {} using {}", to, config.getProvider());
            return true;
        }).onErrorResume(e -> {
            log.warn("⚠️ Send attempt failed with [{}]: {}", config.getProvider(), e.getMessage());
            return Mono.just(false);
        });
    }

    private Mono<Boolean> fallback(NotificationConfig config, String to, String message, List<File> attachments) {
        if (StringUtils.isNotBlank(config.getFallbackConfigId())) {
            return configRepository.findById(config.getFallbackConfigId())
                    .flatMap(fallback -> trySend(fallback, to, message, attachments))
                    .defaultIfEmpty(false);
        }

        if (config.getPrivacyFallbackConfig() != null && !config.getPrivacyFallbackConfig().isEmpty()) {
            NotificationConfig dynamic = new NotificationConfig();
            dynamic.setConfig(config.getPrivacyFallbackConfig());
            dynamic.setProvider((String) config.getPrivacyFallbackConfig().get("provider"));
            dynamic.setClientName(config.getClientName());
            dynamic.setChannel(config.getChannel());
            dynamic.setActive(true);
            return trySend(dynamic, to, message, attachments);
        }

        return Mono.just(false);
    }

    private Mono<Void> logFailure(String configId, String templateId, String to, String message) {
        log.error("❌ WhatsApp delivery failed for all configurations.");
        FailedWhatsappLog failure = FailedWhatsappLog.builder()
                .notificationConfigId(configId)
                .templateId(templateId)
                .phoneNumber(to)
                .message(message)
                .errorMessage("All fallback failed")
                .timestamp(System.currentTimeMillis())
                .build();
         failedLogService.save(failure);
         return Mono.empty();
    }

    private String extractKey(String url) {
        return url.substring(url.lastIndexOf("/") + 1);
    }
}
