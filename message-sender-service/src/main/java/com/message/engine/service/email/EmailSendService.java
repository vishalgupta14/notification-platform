package com.message.engine.service.email;

import com.message.engine.manager.EmailConnectionPoolManager;
import com.message.engine.manager.FileStorageConnectionPoolManager;
import com.message.engine.service.FailedAttachmentLogService;
import com.message.engine.service.FailedEmailLogService;
import com.message.engine.service.FileStorageConfigService;
import com.notification.common.dto.CachedStorageClient;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.model.*;
import com.notification.common.repository.NotificationConfigRepository;
import com.notification.common.service.upload.FileUploader;
import com.notification.common.service.upload.HtmlCdnUploader;
import jakarta.mail.internet.MimeMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.*;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSendService {

    private final NotificationConfigRepository notificationConfigRepository;
    private final FileStorageConfigService fileStorageConfigService;
    private final EmailConnectionPoolManager emailConnectionPoolManager;
    private final FileStorageConnectionPoolManager fileStorageConnectionPoolManager;
    private final FailedEmailLogService failedEmailLogService;
    private final FailedAttachmentLogService failedAttachmentLogService;
    private final HtmlCdnUploader htmlCdnUploader;

    @Value("${notification.email.enabled:true}")
    private boolean isEmailSendingEnabled;

    @Value("${notification.email.allowPartialAttachment:false}")
    private boolean allowPartialAttachment;

    public Mono<Void> sendEmail(NotificationPayloadDTO request) {
        if (!isEmailSendingEnabled) {
            log.info("[EMAIL-SIMULATION] Email sending is DISABLED via configuration.");
            return Mono.empty();
        }

        String templateId = request.getSnapshotTemplate().getId();
        String notificationConfigId = request.getSnapshotConfig().getId();
        String toEmail = request.getTo();
        List<String> cc = request.getCc();
        List<String> bcc = request.getBcc();
        String resolvedSubject = request.getSubject();
        TemplateEntity template = request.getSnapshotTemplate();

        return Mono.fromCallable(() -> {
                    String htmlContent = template.getContent();
                    if (htmlContent == null && StringUtils.isNotBlank(template.getCdnUrl())) {
                        htmlContent = htmlCdnUploader.fetchFromCdn(template.getCdnUrl());
                        htmlCdnUploader.deleteFromCdn(template.getCdnUrl());
                    }
                    return new PreparedEmailContent(resolvedSubject, htmlContent, new ArrayList<>());
                })
                .flatMap(content -> prepareAttachmentsReactive(template)
                        .collectList()
                        .doOnNext(content::setAttachmentFiles)
                        .onErrorResume(e -> {
                            log.warn("Attachment error: {}", e.getMessage());
                            if (!allowPartialAttachment) {
                                return failedAttachmentLogService.save(FailedAttachmentLog.builder()
                                                .templateId(templateId)
                                                .notificationConfigId(notificationConfigId)
                                                .errorMessage("Attachment download failed: " + e.getMessage())
                                                .timestamp(System.currentTimeMillis())
                                                .build())
                                        .then(Mono.error(new RuntimeException("Attachment preparation failed", e)));
                            }
                            return Mono.just(Collections.emptyList());
                        })
                        .thenReturn(content)
                )
                .flatMap(content -> trySendWithFallbacks(request, content))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private Flux<File> prepareAttachmentsReactive(TemplateEntity template) {
        if (template.getAttachments() == null || template.getAttachments().isEmpty()) {
            return Flux.empty();
        }

        return Flux.fromIterable(template.getAttachments())
                .flatMap(ref -> fileStorageConfigService.getById(ref.getFileStorageId())
                        .flatMap(fileStorageConnectionPoolManager::getClient)
                        .flatMapMany(cached -> {
                            FileUploader uploader = cached.getUploader();
                            String key = extractKey(ref.getFileUrl());

                            return Mono.fromCallable(() -> {
                                File tempFile = File.createTempFile("attachment", key);
                                try (InputStream in = uploader.downloadFile(key, cached.getProperties());
                                     FileOutputStream out = new FileOutputStream(tempFile)) {
                                    in.transferTo(out);
                                }
                                return tempFile;
                            }).flux();
                        })
                        .onErrorResume(e -> {
                            log.warn("Attachment skipped due to error: {}", e.getMessage());
                            return Flux.empty();
                        })
                );
    }

    private Mono<Void> trySendWithFallbacks(NotificationPayloadDTO request, PreparedEmailContent content) {
        NotificationConfig mainConfig = request.getSnapshotConfig();
        String toEmail = request.getTo();
        List<String> cc = request.getCc();
        List<String> bcc = request.getBcc();

        return trySend(mainConfig, toEmail, cc, bcc, content)
                .flatMap(success -> {
                    if (success) {
                        return Mono.empty();
                    } else {
                        return fallbackSend(mainConfig, toEmail, cc, bcc, content)
                                .flatMap(fallbackSuccess -> {
                                    if (fallbackSuccess) {
                                        return Mono.empty();
                                    } else {
                                        return logAndFail(request, content);
                                    }
                                });
                    }
                });
    }

    private Mono<Boolean> trySend(NotificationConfig config, String toEmail, List<String> cc, List<String> bcc,
                                  PreparedEmailContent content) {
        return emailConnectionPoolManager.getMailSender(config)
                .flatMap(sender -> Mono.fromCallable(() -> {
                    MimeMessage message = sender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, true);

                    helper.setTo(toEmail);
                    if (cc != null && !cc.isEmpty()) helper.setCc(cc.toArray(new String[0]));
                    if (bcc != null && !bcc.isEmpty()) helper.setBcc(bcc.toArray(new String[0]));

                    helper.setSubject(content.getFinalSubject());
                    helper.setText(content.getHtmlBody(), true);

                    for (File attachment : content.getAttachmentFiles()) {
                        helper.addAttachment(attachment.getName(), attachment);
                    }

                    sender.send(message);
                    log.info("✅ Email sent to {}", toEmail);
                    return true;
                }).subscribeOn(Schedulers.boundedElastic()))
                .onErrorResume(e -> {
                    log.warn("❌ Email sending failed: {}", e.getMessage(), e);
                    return Mono.just(false);
                });
    }


    private Mono<Boolean> fallbackSend(NotificationConfig mainConfig, String to, List<String> cc,
                                       List<String> bcc, PreparedEmailContent content) {
        if (StringUtils.isNotBlank(mainConfig.getFallbackConfigId())) {
            return notificationConfigRepository.findById(mainConfig.getFallbackConfigId())
                    .flatMap(fallbackConfig -> trySend(fallbackConfig, to, cc, bcc, content))
                    .switchIfEmpty(tryPrivacyFallback(mainConfig, to, cc, bcc, content));
        }

        return tryPrivacyFallback(mainConfig, to, cc, bcc, content);
    }


    private Mono<Boolean> tryPrivacyFallback(NotificationConfig mainConfig, String to, List<String> cc,
                                             List<String> bcc, PreparedEmailContent content) {
        Map<String, Object> config = mainConfig.getPrivacyFallbackConfig();
        if (config != null && !config.isEmpty()) {
            NotificationConfig dynamic = new NotificationConfig();
            dynamic.setConfig(config);
            dynamic.setClientName(mainConfig.getClientName());
            dynamic.setProvider(mainConfig.getProvider());
            dynamic.setChannel(mainConfig.getChannel());
            dynamic.setActive(true);
            return trySend(dynamic, to, cc, bcc, content);
        }
        return Mono.just(false);
    }

    private Mono<Void> logAndFail(NotificationPayloadDTO request, PreparedEmailContent content) {
        TemplateEntity template = request.getSnapshotTemplate();
        NotificationConfig config = request.getSnapshotConfig();
        return failedEmailLogService.save(FailedEmailLog.builder()
                        .toEmail(request.getTo())
                        .cc(request.getCc())
                        .bcc(request.getBcc())
                        .subject(content.getFinalSubject())
                        .htmlContent(content.getHtmlBody())
                        .templateId(template.getId())
                        .notificationConfigId(config.getId())
                        .errorMessage("All fallback and privacy fallback config attempts failed.")
                        .timestamp(System.currentTimeMillis())
                        .build())
                .then(Mono.error(new RuntimeException("All fallback attempts failed")));
    }

    private String extractKey(String fileUrl) {
        if (fileUrl == null || !fileUrl.contains("/")) return fileUrl;
        return fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
    }

    @Data
    @AllArgsConstructor
    private static class PreparedEmailContent {
        private String finalSubject;
        private String htmlBody;
        private List<File> attachmentFiles;
    }
}
