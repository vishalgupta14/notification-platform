package com.message.node.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.node.rate.limiter.RateLimiterService;
import com.notification.common.dto.NotificationConfigDTO;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.dto.NotificationRequestDTO;
import com.notification.common.model.NotificationConfig;
import com.notification.common.model.ScheduledNotification;
import com.notification.common.model.TemplateEntity;
import com.message.node.producer.MessageProducer;
import com.message.node.service.NotificationConfigService;
import com.message.node.service.ScheduledNotificationService;
import com.message.node.service.TemplateService;
import com.notification.common.service.upload.HtmlCdnUploader;
import com.notification.common.utils.JsonUtil;
import com.notification.common.utils.TemplateUtil;
import jakarta.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/config")
public class NotificationConfigController {

    private static final Logger log = LoggerFactory.getLogger(NotificationConfigController.class);

    ObjectMapper objectMapper = new ObjectMapper();

    private final NotificationConfigService configService;

    private final MessageProducer messageProducer;

    private final TemplateService templateService;

    private final ScheduledNotificationService scheduledNotificationService;

    private final HtmlCdnUploader htmlCdnUploader;

    private final RateLimiterService rateLimiterService;

    @Value("${email.queue.name}")
    private String emailQueueName;

    @Value("${sms.queue.name}")
    private String smsQueueName;

    @Value("${whatsapp.queue.name}")
    private String whatsappQueueName;

    @Value("${email.cache.eviction}")
    private String emailCacheEvictionQueueName;

    @Value("${email.template.max.inline.kb:100}")
    private int maxInlineKb;

    @Value("${push.queue.name}")
    private String pushQueueName;

    @Value("${voice.queue.name}")
    private String voiceQueueName;

    @Value("${webhook.queue.name}")
    private String webhookQueueName;

    @Value("${publish.queue.name}")
    private String publishQueueName;


    public NotificationConfigController(NotificationConfigService configService, MessageProducer messageProducer, TemplateService templateService, ScheduledNotificationService scheduledNotificationService, HtmlCdnUploader htmlCdnUploader, RateLimiterService rateLimiterService) {
        this.configService = configService;
        this.messageProducer = messageProducer;
        this.templateService = templateService;
        this.scheduledNotificationService = scheduledNotificationService;
        this.htmlCdnUploader = htmlCdnUploader;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping
    public ResponseEntity<NotificationConfigDTO> saveConfig(@RequestBody NotificationConfig config) {
        log.info("Received request to save config for clientId={}, channel={}", config.getClientName(), config.getChannel());
        NotificationConfigDTO dto = configService.toDTO(configService.save(config));
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/ui/{clientId}/{channel}")
    public ResponseEntity<NotificationConfigDTO> getConfigForUI(@PathVariable String clientId,
                                                                @PathVariable String channel) {
        log.info("Fetching active config for clientId={}, channel={}", clientId, channel);
        NotificationConfig config = configService.getActiveConfig(clientId, channel);
        NotificationConfigDTO dto = configService.toDTO(config);
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<NotificationConfigDTO> updateConfig(@PathVariable String id,
                                                              @RequestBody NotificationConfig updated) throws JsonProcessingException {
        log.info("Updating config with id={}", id);
        NotificationConfig existing = configService.findById(id);

        updated.setId(id);
        updated.setCreatedAt(existing.getCreatedAt());
        updated.setUpdatedAt(LocalDateTime.now());

        NotificationConfigDTO dto = configService.toDTO(configService.save(updated));
        Map<String, String> payloadMap = new HashMap<>();
        payloadMap.put("notificationConfigId", id);
        String messagePayload = objectMapper.writeValueAsString(payloadMap);
        messageProducer.sendMessage(emailCacheEvictionQueueName,messagePayload,true);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConfig(@PathVariable String id) throws JsonProcessingException {
        log.warn("Deleting config with id={}", id);
        configService.deleteById(id);
        Map<String, String> payloadMap = new HashMap<>();
        payloadMap.put("notificationConfigId", id);
        String messagePayload = objectMapper.writeValueAsString(payloadMap);
        messageProducer.sendMessage(emailCacheEvictionQueueName,messagePayload,true);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/all")
    public ResponseEntity<List<NotificationConfigDTO>> getAllConfigsForUI() {
        log.info("Fetching all configs for UI");
        List<NotificationConfigDTO> configDTOs = configService.findAll()
                .stream()
                .map(configService::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(configDTOs);
    }

    @PostMapping("/send-email")
    public ResponseEntity<String> sendNotification(@Valid @RequestBody NotificationRequestDTO requestDTO) {
        log.info("Received notification send request with configId={}, templateId={}",
                requestDTO.getNotificationConfigId(), requestDTO.getTemplateId());

        if (!rateLimiterService.isAllowed(emailQueueName)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("🚫 Rate limit exceeded for email channel");
        }

        NotificationConfig config = configService.findById(requestDTO.getNotificationConfigId());
        if (config == null || !config.isActive()) {
            return ResponseEntity.badRequest().body("Invalid or inactive NotificationConfig ID");
        }

        TemplateEntity template = templateService.getTemplateById(requestDTO.getTemplateId())
                .orElse(null);
        if (template == null) {
            return ResponseEntity.badRequest().body("Invalid Template ID: Template not found");
        }

        if (requestDTO.isScheduled()) {
                ScheduledNotification scheduled = ScheduledNotification.builder()
                        .notificationConfig(config)
                        .template(template)
                        .to(requestDTO.getTo())
                        .cc(requestDTO.getCc())
                        .bcc(requestDTO.getBcc())
                        .emailSubject(requestDTO.getEmailSubject())
                        .customParams(requestDTO.getCustomParams())
                        .queueName(emailQueueName)
                        .scheduleCron(requestDTO.getScheduleCron())
                        .active(true)
                        .build();
                scheduledNotificationService.saveScheduledNotification(scheduled);
            return ResponseEntity.ok("✅ Scheduled emails stored for all recipients.");
        }

        try {
            int maxInlineChars = maxInlineKb * 1024;
            String resolvedHtml = template.getContent();
            resolvedHtml = TemplateUtil.resolveTemplateWithParams(resolvedHtml, requestDTO.getCustomParams());

            if (resolvedHtml != null && resolvedHtml.length() > maxInlineChars) {
                String cdnUrl = htmlCdnUploader.uploadHtmlAsFile(resolvedHtml);
                template.setCdnUrl(cdnUrl);
                template.setContent(null);
            } else {
                template.setContent(resolvedHtml);
            }

            String resolvedSubject = TemplateUtil.resolveTemplateWithParams(
                    StringUtils.defaultIfBlank(requestDTO.getEmailSubject(), template.getEmailSubject()),
                    requestDTO.getCustomParams());

                NotificationPayloadDTO payload = new NotificationPayloadDTO();
                payload.setTo(requestDTO.getTo());
                payload.setCc(requestDTO.getCc());
                payload.setBcc(requestDTO.getBcc());
                payload.setSubject(resolvedSubject);
                payload.setSnapshotConfig(config);
                payload.setSnapshotTemplate(template);

                String jsonPayload = JsonUtil.toJsonWithJavaTime(payload);
                messageProducer.sendMessage(emailQueueName, jsonPayload, false);

        } catch (IOException e) {
            log.error("Failed to serialize or upload NotificationPayloadDTO", e);
            return ResponseEntity.internalServerError().body("❌ Failed to send notification");
        }

        return ResponseEntity.ok("✅ Email request processed for all recipients.");
    }

    @PostMapping("/send-sms")
    public ResponseEntity<String> sendSms(@Valid @RequestBody NotificationRequestDTO requestDTO) {
        log.info("📲 Received SMS send request with configId={}, templateId={}",
                requestDTO.getNotificationConfigId(), requestDTO.getTemplateId());

        if (!rateLimiterService.isAllowed(smsQueueName)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("🚫 Rate limit exceeded for email channel");
        }

        NotificationConfig config = configService.findById(requestDTO.getNotificationConfigId());
        if (config == null || !config.isActive()) {
            return ResponseEntity.badRequest().body("❌ Invalid or inactive NotificationConfig ID");
        }

        TemplateEntity template = templateService.getTemplateById(requestDTO.getTemplateId())
                .orElse(null);
        if (template == null) {
            return ResponseEntity.badRequest().body("❌ Invalid Template ID: Template not found");
        }

        if (requestDTO.isScheduled()) {
            ScheduledNotification scheduled = ScheduledNotification.builder()
                    .notificationConfig(config)
                    .template(template)
                    .to(requestDTO.getTo())
                    .emailSubject(requestDTO.getEmailSubject())
                    .customParams(requestDTO.getCustomParams())
                    .queueName(smsQueueName)
                    .scheduleCron(requestDTO.getScheduleCron())
                    .active(true)
                    .build();
            scheduledNotificationService.saveScheduledNotification(scheduled);
            return ResponseEntity.ok("✅ Scheduled SMS stored for processing.");
        }

        String resolvedMessage = TemplateUtil.resolveTemplateWithParams(template.getContent(), requestDTO.getCustomParams());

        NotificationPayloadDTO payload = new NotificationPayloadDTO();
        payload.setTo(requestDTO.getTo());
        payload.setSubject(resolvedMessage);
        payload.setSnapshotConfig(config);
        payload.setSnapshotTemplate(template);

        String jsonPayload = JsonUtil.toJsonWithJavaTime(payload);
        messageProducer.sendMessage(smsQueueName, jsonPayload, false);

        return ResponseEntity.ok("✅ SMS request processed successfully.");
    }

    @PostMapping("/send-whatsapp")
    public ResponseEntity<String> sendWhatsApp(@Valid @RequestBody NotificationRequestDTO requestDTO) {
        log.info("📲 Received WhatsApp send request with configId={}, templateId={}",
                requestDTO.getNotificationConfigId(), requestDTO.getTemplateId());

        if (!rateLimiterService.isAllowed(whatsappQueueName)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("🚫 Rate limit exceeded for email channel");
        }

        NotificationConfig config = configService.findById(requestDTO.getNotificationConfigId());
        if (config == null || !config.isActive()) {
            return ResponseEntity.badRequest().body("❌ Invalid or inactive NotificationConfig ID");
        }

        TemplateEntity template = templateService.getTemplateById(requestDTO.getTemplateId()).orElse(null);
        if (template == null) {
            return ResponseEntity.badRequest().body("❌ Invalid Template ID: Template not found");
        }

        if (requestDTO.isScheduled()) {
            ScheduledNotification scheduled = ScheduledNotification.builder()
                    .notificationConfig(config)
                    .template(template)
                    .to(requestDTO.getTo())
                    .emailSubject(null)
                    .customParams(requestDTO.getCustomParams())
                    .queueName(whatsappQueueName)
                    .scheduleCron(requestDTO.getScheduleCron())
                    .active(true)
                    .build();
            scheduledNotificationService.saveScheduledNotification(scheduled);
            return ResponseEntity.ok("✅ Scheduled WhatsApp message stored.");
        }

        try {
            int maxInlineChars = maxInlineKb * 1024;
            String resolvedMessage = TemplateUtil.resolveTemplateWithParams(template.getContent(), requestDTO.getCustomParams());

            if (resolvedMessage != null && resolvedMessage.length() > maxInlineChars) {
                String cdnUrl = htmlCdnUploader.uploadHtmlAsFile(resolvedMessage);
                template.setCdnUrl(cdnUrl);
                template.setContent(null);
            } else {
                template.setContent(resolvedMessage);
            }

            NotificationPayloadDTO payload = new NotificationPayloadDTO();
            payload.setTo(requestDTO.getTo());
            payload.setSubject(resolvedMessage);
            payload.setSnapshotConfig(config);
            payload.setSnapshotTemplate(template);

            String jsonPayload = JsonUtil.toJsonWithJavaTime(payload);
            messageProducer.sendMessage(whatsappQueueName, jsonPayload, false);

        } catch (IOException e) {
            log.error("❌ Failed to serialize or upload WhatsApp payload", e);
            return ResponseEntity.internalServerError().body("❌ Failed to send WhatsApp notification");
        }

        return ResponseEntity.ok("✅ WhatsApp request processed successfully.");
    }

    @PostMapping("/send-push")
    public ResponseEntity<String> sendPushNotification(@Valid @RequestBody NotificationRequestDTO requestDTO) {
        log.info("📳 Received Push Notification send request with configId={}, templateId={}",
                requestDTO.getNotificationConfigId(), requestDTO.getTemplateId());

        if (!rateLimiterService.isAllowed(pushQueueName)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("🚫 Rate limit exceeded for email channel");
        }


        NotificationConfig config = configService.findById(requestDTO.getNotificationConfigId());
        if (config == null || !config.isActive()) {
            return ResponseEntity.badRequest().body("❌ Invalid or inactive NotificationConfig ID");
        }

        TemplateEntity template = templateService.getTemplateById(requestDTO.getTemplateId()).orElse(null);
        if (template == null) {
            return ResponseEntity.badRequest().body("❌ Invalid Template ID: Template not found");
        }

        if (requestDTO.isScheduled()) {
            ScheduledNotification scheduled = ScheduledNotification.builder()
                    .notificationConfig(config)
                    .template(template)
                    .to(requestDTO.getTo())
                    .emailSubject(null)
                    .customParams(requestDTO.getCustomParams())
                    .queueName(pushQueueName)
                    .scheduleCron(requestDTO.getScheduleCron())
                    .active(true)
                    .build();
            scheduledNotificationService.saveScheduledNotification(scheduled);
            return ResponseEntity.ok("✅ Scheduled push notification stored.");
        }

        String resolvedMessage = TemplateUtil.resolveTemplateWithParams(template.getContent(), requestDTO.getCustomParams());

        template.setContent(resolvedMessage);
        NotificationPayloadDTO payload = new NotificationPayloadDTO();
        payload.setTo(requestDTO.getTo());
        payload.setSubject(resolvedMessage);
        payload.setSnapshotConfig(config);
        payload.setSnapshotTemplate(template);

        String jsonPayload = JsonUtil.toJsonWithJavaTime(payload);
        messageProducer.sendMessage(pushQueueName, jsonPayload, false);

        return ResponseEntity.ok("✅ Push notification request queued successfully.");
    }

    @PostMapping("/send-voice")
    public ResponseEntity<String> sendVoiceNotification(@Valid @RequestBody NotificationRequestDTO requestDTO) {
        log.info("📞 Received voice call request with configId={}, templateId={}",
                requestDTO.getNotificationConfigId(), requestDTO.getTemplateId());

        if (!rateLimiterService.isAllowed(voiceQueueName)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("🚫 Rate limit exceeded for email channel");
        }


        NotificationConfig config = configService.findById(requestDTO.getNotificationConfigId());
        if (config == null || !config.isActive()) {
            return ResponseEntity.badRequest().body("❌ Invalid or inactive NotificationConfig ID");
        }

        TemplateEntity template = templateService.getTemplateById(requestDTO.getTemplateId())
                .orElse(null);
        if (template == null) {
            return ResponseEntity.badRequest().body("❌ Invalid Template ID: Template not found");
        }

        if (requestDTO.isScheduled()) {
            ScheduledNotification scheduled = ScheduledNotification.builder()
                    .notificationConfig(config)
                    .template(template)
                    .to(requestDTO.getTo())
                    .customParams(requestDTO.getCustomParams())
                    .queueName(voiceQueueName)
                    .scheduleCron(requestDTO.getScheduleCron())
                    .active(true)
                    .build();
            scheduledNotificationService.saveScheduledNotification(scheduled);
            return ResponseEntity.ok("✅ Scheduled voice call stored.");
        }

        try {
            int maxInlineChars = maxInlineKb * 1024;
            String resolvedTwiML = template.getContent();
            resolvedTwiML = TemplateUtil.resolveTemplateWithParams(resolvedTwiML, requestDTO.getCustomParams());

            if (resolvedTwiML != null && resolvedTwiML.length() > maxInlineChars) {
                String cdnUrl = htmlCdnUploader.uploadHtmlAsFile(resolvedTwiML);
                template.setCdnUrl(cdnUrl);
                template.setContent(null);
            } else {
                template.setContent(resolvedTwiML);
            }

            NotificationPayloadDTO payload = new NotificationPayloadDTO();
            payload.setTo(requestDTO.getTo());
            payload.setSnapshotConfig(config);
            payload.setSnapshotTemplate(template);

            String jsonPayload = JsonUtil.toJsonWithJavaTime(payload);
            messageProducer.sendMessage(voiceQueueName, jsonPayload, false);

        } catch (IOException e) {
            log.error("❌ Failed to serialize or upload voice payload", e);
            return ResponseEntity.internalServerError().body("❌ Failed to send voice call");
        }

        return ResponseEntity.ok("✅ Voice call request processed successfully.");
    }

    @PostMapping("/send-webhook")
    public ResponseEntity<String> sendWebhook(@Valid @RequestBody NotificationRequestDTO requestDTO) {
        log.info("🌐 Webhook request received: configId={}, templateId={}",
                requestDTO.getNotificationConfigId(), requestDTO.getTemplateId());

        NotificationConfig config = configService.findById(requestDTO.getNotificationConfigId());
        if (config == null || !config.isActive()) {
            return ResponseEntity.badRequest().body("❌ Invalid or inactive NotificationConfig ID");
        }

        TemplateEntity template = templateService.getTemplateById(requestDTO.getTemplateId()).orElse(null);
        if (template == null) {
            return ResponseEntity.badRequest().body("❌ Invalid Template ID");
        }

        if (requestDTO.isScheduled()) {
            ScheduledNotification scheduled = ScheduledNotification.builder()
                    .notificationConfig(config)
                    .template(template)
                    .to(requestDTO.getTo())
                    .customParams(requestDTO.getCustomParams())
                    .queueName(webhookQueueName)
                    .scheduleCron(requestDTO.getScheduleCron())
                    .active(true)
                    .build();
            scheduledNotificationService.saveScheduledNotification(scheduled);
            return ResponseEntity.ok("✅ Scheduled voice call stored.");
        }

        String resolvedJson = TemplateUtil.resolveTemplateWithParams(template.getContent(), requestDTO.getCustomParams());
        template.setContent(resolvedJson);
        NotificationPayloadDTO payload = new NotificationPayloadDTO();
        payload.setTo(requestDTO.getTo());
        payload.setSubject(null);
        payload.setSnapshotConfig(config);
        payload.setSnapshotTemplate(template);

        String jsonPayload = JsonUtil.toJsonWithJavaTime(payload);
        messageProducer.sendMessage(webhookQueueName, jsonPayload, false);

        return ResponseEntity.ok("✅ Webhook queued successfully.");
    }

    @PostMapping("/send-queue")
    public ResponseEntity<String> sendToQueue(@Valid @RequestBody NotificationRequestDTO requestDTO) {
        log.info("📤 Received send-queue request with configId={}, templateId={}, queue={}",
                requestDTO.getNotificationConfigId(), requestDTO.getTemplateId(), requestDTO.getTo());

        if (!rateLimiterService.isAllowed(publishQueueName)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("🚫 Rate limit exceeded for queue: " + publishQueueName);
        }

        NotificationConfig config = configService.findById(requestDTO.getNotificationConfigId());
        if (config == null || !config.isActive()) {
            return ResponseEntity.badRequest().body("❌ Invalid or inactive NotificationConfig ID");
        }

        TemplateEntity template = templateService.getTemplateById(requestDTO.getTemplateId()).orElse(null);
        if (template == null) {
            return ResponseEntity.badRequest().body("❌ Invalid Template ID: Not found");
        }

        if (requestDTO.isScheduled()) {
            ScheduledNotification scheduled = ScheduledNotification.builder()
                    .notificationConfig(config)
                    .template(template)
                    .to(requestDTO.getTo())
                    .cc(requestDTO.getCc())
                    .bcc(requestDTO.getBcc())
                    .emailSubject(requestDTO.getEmailSubject())
                    .customParams(requestDTO.getCustomParams())
                    .queueName(publishQueueName)
                    .scheduleCron(requestDTO.getScheduleCron())
                    .active(true)
                    .build();

            scheduledNotificationService.saveScheduledNotification(scheduled);
            return ResponseEntity.ok("✅ Scheduled notification saved for future dispatch.");
        }

        try {
            // Resolve template content
            int maxInlineChars = maxInlineKb * 1024;
            String resolvedHtml = TemplateUtil.resolveTemplateWithParams(template.getContent(), requestDTO.getCustomParams());

            if (resolvedHtml != null && resolvedHtml.length() > maxInlineChars) {
                String cdnUrl = htmlCdnUploader.uploadHtmlAsFile(resolvedHtml);
                template.setCdnUrl(cdnUrl);
                template.setContent(null);
            } else {
                template.setContent(resolvedHtml);
            }

            String resolvedSubject = TemplateUtil.resolveTemplateWithParams(
                    StringUtils.defaultIfBlank(requestDTO.getEmailSubject(), template.getEmailSubject()),
                    requestDTO.getCustomParams());

            NotificationPayloadDTO payload = new NotificationPayloadDTO();
            payload.setTo(requestDTO.getTo());
            payload.setCc(requestDTO.getCc());
            payload.setBcc(requestDTO.getBcc());
            payload.setSubject(resolvedSubject);
            payload.setSnapshotConfig(config);
            payload.setSnapshotTemplate(template);

            String jsonPayload = JsonUtil.toJsonWithJavaTime(payload);

            messageProducer.sendMessage(publishQueueName, jsonPayload, false);

        } catch (Exception e) {
            log.error("❌ Failed to process and send message", e);
            return ResponseEntity.internalServerError().body("❌ Notification failed: " + e.getMessage());
        }

        return ResponseEntity.ok("✅ Message published to queue: " + requestDTO.getTo());
    }

}
