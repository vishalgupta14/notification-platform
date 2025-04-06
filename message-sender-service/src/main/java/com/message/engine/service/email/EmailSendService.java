package com.message.engine.service.email;

import com.message.engine.service.FailedAttachmentLogService;
import com.message.engine.service.FailedEmailLogService;
import com.message.engine.service.FileStorageConfigService;
import com.notification.common.dto.CachedStorageClient;
import com.notification.common.dto.NotificationPayloadDTO;
import com.message.engine.manager.EmailConnectionPoolManager;
import com.message.engine.manager.FileStorageConnectionPoolManager;
import com.notification.common.model.*;
import com.notification.common.repository.NotificationConfigRepository;
import com.notification.common.service.upload.FileUploader;
import com.notification.common.service.upload.HtmlCdnUploader;
import jakarta.mail.internet.MimeMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailSendService {

    private static final Logger log = LoggerFactory.getLogger(EmailSendService.class);

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

    public void sendEmail(NotificationPayloadDTO request) {
        long startTime = System.currentTimeMillis();
        String notificationConfigId = request.getSnapshotConfig().getId();
        String templateId = request.getSnapshotTemplate().getId();
        String toEmail = request.getTo();
        List<String> cc = request.getCc();
        List<String> bcc = request.getBcc();
        String resolvedSubject = request.getSubject();

        TemplateEntity template = request.getSnapshotTemplate();
        NotificationConfig mainConfig = request.getSnapshotConfig();

        if (!isEmailSendingEnabled) {
            log.info("[EMAIL-SIMULATION] Email sending is DISABLED via configuration.");
            return;
        }

        try {
            String htmlContent = template.getContent();
            if (htmlContent == null && StringUtils.isNotBlank(template.getCdnUrl())) {
                htmlContent = htmlCdnUploader.fetchFromCdn(template.getCdnUrl());
                htmlCdnUploader.deleteFromCdn(template.getCdnUrl());
            }

            PreparedEmailContent content = new PreparedEmailContent(resolvedSubject, htmlContent, new ArrayList<>());

            try {
                content.setAttachmentFiles(prepareAttachments(template));
            } catch (IOException e) {
                log.warn("Attachment preparation failed: {}", e.getMessage());

                if (!allowPartialAttachment) {
                    failedAttachmentLogService.save(FailedAttachmentLog.builder()
                            .templateId(templateId)
                            .notificationConfigId(notificationConfigId)
                            .errorMessage("Attachment download failed: " + e.getMessage())
                            .timestamp(System.currentTimeMillis())
                            .build());
                    throw new RuntimeException("Attachment preparation failed", e);
                }

                log.warn("Proceeding without attachments due to allowPartialAttachment=true");
            }

            // Try sending
            if (trySend(mainConfig, toEmail, cc, bcc, content)) return;

            // Fallback
            final PreparedEmailContent finalContent = content;

            if (StringUtils.isNotBlank(mainConfig.getFallbackConfigId())) {
                NotificationConfig fallbackConfig = notificationConfigRepository
                        .findById(mainConfig.getFallbackConfigId())
                        .orElse(null);

                if (fallbackConfig != null && trySend(fallbackConfig, toEmail, cc, bcc, finalContent)) return;
            }

            // Privacy fallback
            if (mainConfig.getPrivacyFallbackConfig() != null && !mainConfig.getPrivacyFallbackConfig().isEmpty()) {
                NotificationConfig dynamicConfig = new NotificationConfig();
                dynamicConfig.setConfig(mainConfig.getPrivacyFallbackConfig());
                dynamicConfig.setClientName(mainConfig.getClientName());
                dynamicConfig.setChannel(mainConfig.getChannel());
                dynamicConfig.setProvider(mainConfig.getProvider());
                dynamicConfig.setActive(true);

                if (trySend(dynamicConfig, toEmail, cc, bcc, finalContent)) return;
            }

            // All failed
            log.error("All attempts to send email failed for template: {}", templateId);

            failedEmailLogService.save(FailedEmailLog.builder()
                    .toEmail(toEmail)
                    .cc(cc)
                    .bcc(bcc)
                    .subject(resolvedSubject)
                    .htmlContent(htmlContent)
                    .templateId(templateId)
                    .notificationConfigId(notificationConfigId)
                    .errorMessage("All fallback and privacy fallback config attempts failed.")
                    .timestamp(System.currentTimeMillis())
                    .build());

            throw new RuntimeException("Failed to send email after all fallback attempts.");

        } catch (Exception e) {
            log.error("Exception occurred while sending email: {}", e.getMessage(), e);
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("⏱️ Email sending process completed in {} ms", duration);
        }
    }


    private boolean trySend(NotificationConfig config, String toEmail, List<String> cc, List<String> bcc,
                            PreparedEmailContent content) {
        try {
            JavaMailSender sender = emailConnectionPoolManager.getMailSender(config);
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
            log.info("Email sent to {} using config {}", toEmail, config.getClientName());
            return true;

        } catch (Exception e) {
            log.warn("Attempt failed with config [{}]: {}", config.getClientName(), e.getMessage());
            return false;
        }
    }

    private List<File> prepareAttachments(TemplateEntity template) throws IOException {
        List<File> attachmentFiles = new ArrayList<>();
        if (template.getAttachments() != null) {
            for (FileReference ref : template.getAttachments()) {
                FileStorageConfig storageConfig = fileStorageConfigService.getById(ref.getFileStorageId());
                CachedStorageClient cachedClient = fileStorageConnectionPoolManager.getClient(storageConfig);
                FileUploader uploader = cachedClient.getUploader();
                Map<String, Object> properties = cachedClient.getProperties();

                String keyOrFilename = extractKey(ref.getFileUrl());
                File tempFile = File.createTempFile("attachment", keyOrFilename);

                try (InputStream in = uploader.downloadFile(keyOrFilename, properties);
                     FileOutputStream out = new FileOutputStream(tempFile)) {
                    in.transferTo(out);
                }

                attachmentFiles.add(tempFile);
            }
        }
        return attachmentFiles;
    }

    private String mergeTemplateWithParams(String htmlContent, Map<String, Object> params) {
        if (params == null || params.isEmpty()) return htmlContent;

        String result = htmlContent;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            result = result.replace(placeholder, String.valueOf(entry.getValue()));
        }
        return result;
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
