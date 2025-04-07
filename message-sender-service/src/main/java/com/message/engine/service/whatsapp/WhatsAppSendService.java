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
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class WhatsAppSendService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppSendService.class);

    private final NotificationConfigRepository configRepository;
    private final FileStorageConfigService fileStorageConfigService;
    private final FileStorageConnectionPoolManager fileStorageConnectionPoolManager;
    private final WhatsAppSenderFactory senderFactory;
    private final FailedWhatsappLogService failedLogService;

    @Value("${notification.whatsapp.enabled:true}")
    private boolean isWhatsAppEnabled;

    public void sendWhatsApp(NotificationPayloadDTO request) {
        String configId = request.getSnapshotConfig().getId();
        String templateId = request.getSnapshotTemplate().getId();
        String to = request.getTo();
        String message = request.getSnapshotTemplate().getContent();

        NotificationConfig config = request.getSnapshotConfig();

        if (!isWhatsAppEnabled) {
            log.info("[WHATSAPP-SIMULATION] Sending is DISABLED");
            return;
        }

        List<File> attachments = new ArrayList<>();
        try {
            attachments = prepareAttachments(request.getSnapshotTemplate());
        } catch (Exception e) {
            log.warn("⚠️ Failed to prepare attachments. Proceeding without them: {}", e.getMessage());
        }

        if (trySend(config, to, message, attachments)) return;

        // Fallback
        if (StringUtils.isNotBlank(config.getFallbackConfigId())) {
            NotificationConfig fallback = configRepository.findById(config.getFallbackConfigId()).orElse(null);
            if (fallback != null && trySend(fallback, to, message, attachments)) return;
        }

        // Privacy Fallback
        if (config.getPrivacyFallbackConfig() != null && !config.getPrivacyFallbackConfig().isEmpty()) {
            NotificationConfig dynamic = new NotificationConfig();
            dynamic.setConfig(config.getPrivacyFallbackConfig());
            dynamic.setProvider((String) config.getPrivacyFallbackConfig().get("provider"));
            dynamic.setClientName(config.getClientName());
            dynamic.setChannel(config.getChannel());
            dynamic.setActive(true);

            if (trySend(dynamic, to, message, attachments)) return;
        }

        log.error("❌ WhatsApp delivery failed for all configurations.");

        failedLogService.save(FailedWhatsappLog.builder()
                .phoneNumber(to)
                .message(message)
                .templateId(templateId)
                .notificationConfigId(configId)
                .errorMessage("All fallback failed")
                .timestamp(System.currentTimeMillis())
                .build());
    }

    private boolean trySend(NotificationConfig config, String to, String message, List<File> attachments) {
        try {
            Map<String, Object> resolvedConfig = config.getConfig();
            WhatsAppSender sender = senderFactory.getSender(config.getProvider()+"-"+config.getChannel());
            sender.sendWhatsApp(resolvedConfig, to, message, attachments);
            log.info("✅ WhatsApp sent to {} using {}", to, config.getProvider());
            return true;
        } catch (Exception e) {
            log.warn("⚠️ Attempt failed with [{}]: {}", config.getProvider(), e.getMessage());
            return false;
        }
    }

    private List<File> prepareAttachments(TemplateEntity template) throws IOException {
        List<File> files = new ArrayList<>();
        if (template.getAttachments() != null) {
            for (FileReference ref : template.getAttachments()) {
                FileStorageConfig storage = fileStorageConfigService.getById(ref.getFileStorageId());
                CachedStorageClient cached = fileStorageConnectionPoolManager.getClient(storage);
                FileUploader uploader = cached.getUploader();

                String key = extractKey(ref.getFileUrl());
                File file = File.createTempFile("wa-", key);
                try (InputStream in = uploader.downloadFile(key, cached.getProperties());
                     FileOutputStream out = new FileOutputStream(file)) {
                    in.transferTo(out);
                }

                files.add(file);
            }
        }
        return files;
    }

    private String extractKey(String url) {
        return url.substring(url.lastIndexOf("/") + 1);
    }
}

