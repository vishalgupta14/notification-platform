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
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class NotificationConfigController {

    private static final Logger log = LoggerFactory.getLogger(NotificationConfigController.class);

    private final NotificationConfigService configService;
    private final MessageProducer messageProducer;
    private final TemplateService templateService;
    private final ScheduledNotificationService scheduledNotificationService;
    private final HtmlCdnUploader htmlCdnUploader;
    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${email.queue.name}")
    private String emailQueueName;
    @Value("${sms.queue.name}")
    private String smsQueueName;
    @Value("${whatsapp.queue.name}")
    private String whatsappQueueName;
    @Value("${push.queue.name}")
    private String pushQueueName;
    @Value("${voice.queue.name}")
    private String voiceQueueName;
    @Value("${webhook.queue.name}")
    private String webhookQueueName;
    @Value("${publish.queue.name}")
    private String publishQueueName;
    @Value("${email.cache.eviction}")
    private String emailCacheEvictionQueueName;
    @Value("${email.template.max.inline.kb:100}")
    private int maxInlineKb;

    @PostMapping
    public Mono<ResponseEntity<NotificationConfigDTO>> saveConfig(@RequestBody NotificationConfig config) {
        log.info("Saving config for client={}, channel={}", config.getClientName(), config.getChannel());
        return configService.save(config)
                .map(configService::toDTO)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/ui/{clientId}/{channel}")
    public Mono<ResponseEntity<NotificationConfigDTO>> getConfigForUI(@PathVariable String clientId,
                                                                      @PathVariable String channel) {
        return configService.getActiveConfig(clientId, channel)
                .map(configService::toDTO)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<NotificationConfigDTO>> updateConfig(@PathVariable String id,
                                                                    @RequestBody NotificationConfig updated) {
        return configService.findById(id)
                .flatMap(existing -> {
                    updated.setId(id);
                    updated.setCreatedAt(existing.getCreatedAt());
                    updated.setUpdatedAt(LocalDateTime.now());
                    return configService.save(updated);
                })
                .map(configService::toDTO)
                .flatMap(dto -> {
                    try {
                        String msg = objectMapper.writeValueAsString(Map.of("notificationConfigId", id));
                        messageProducer.sendMessage(emailCacheEvictionQueueName, msg, true);
                    } catch (JsonProcessingException e) {
                        log.warn("Could not serialize update message", e);
                    }
                    return Mono.just(ResponseEntity.ok(dto));
                });
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteConfig(@PathVariable String id) {
        return configService.deleteById(id)
                .doOnSuccess(unused -> {
                    try {
                        String msg = objectMapper.writeValueAsString(Map.of("notificationConfigId", id));
                        messageProducer.sendMessage(emailCacheEvictionQueueName, msg, true);
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to send eviction message", e);
                    }
                })
                .thenReturn(ResponseEntity.noContent().build());
    }

    @GetMapping("/all")
    public Flux<NotificationConfigDTO> getAllConfigsForUI() {
        return configService.findAll().map(configService::toDTO);
    }

    @PostMapping("/send-email")
    public Mono<ResponseEntity<String>> sendNotification(@Valid @RequestBody NotificationRequestDTO requestDTO) {
        log.info("Received notification send request with configId={}, templateId={}",
                requestDTO.getNotificationConfigId(), requestDTO.getTemplateId());

        if (!rateLimiterService.isAllowed(emailQueueName)) {
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("🚫 Rate limit exceeded for email channel"));
        }

        return Mono.zip(
                configService.findById(requestDTO.getNotificationConfigId()),
                templateService.getTemplateById(requestDTO.getTemplateId())
        ).flatMap(tuple -> {
            NotificationConfig config = tuple.getT1();
            TemplateEntity template = tuple.getT2();

            if (config == null || !config.isActive()) {
                return Mono.just(ResponseEntity.badRequest().body("❌ Invalid or inactive NotificationConfig ID"));
            }
            if (template == null) {
                return Mono.just(ResponseEntity.badRequest().body("❌ Invalid Template ID: Template not found"));
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
                return Mono.just(ResponseEntity.ok("✅ Scheduled emails stored for all recipients."));
            }

            return Mono.fromCallable(() -> {
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
                messageProducer.sendMessage(emailQueueName, jsonPayload, false);

                return ResponseEntity.ok("✅ Email request processed for all recipients.");
            }).onErrorResume(e -> {
                log.error("❌ Failed to serialize or upload NotificationPayloadDTO", e);
                return Mono.just(ResponseEntity.internalServerError().body("❌ Failed to send notification"));
            });
        });
    }

    @PostMapping("/send-sms")
    public Mono<ResponseEntity<String>> sendSms(@Valid @RequestBody NotificationRequestDTO requestDTO) {
        log.info("📲 Received SMS send request with configId={}, templateId={}",
                requestDTO.getNotificationConfigId(), requestDTO.getTemplateId());

        if (!rateLimiterService.isAllowed(smsQueueName)) {
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("🚫 Rate limit exceeded for email channel"));
        }

        return Mono.zip(
                configService.findById(requestDTO.getNotificationConfigId()),
                templateService.getTemplateById(requestDTO.getTemplateId())
        ).flatMap(tuple -> {
            NotificationConfig config = tuple.getT1();
            TemplateEntity template = tuple.getT2();

            if (config == null || !config.isActive()) {
                return Mono.just(ResponseEntity.badRequest().body("❌ Invalid or inactive NotificationConfig ID"));
            }
            if (template == null) {
                return Mono.just(ResponseEntity.badRequest().body("❌ Invalid Template ID: Template not found"));
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
                return Mono.just(ResponseEntity.ok("✅ Scheduled SMS stored for processing."));
            }

            return Mono.fromCallable(() -> {
                String resolvedMessage = TemplateUtil.resolveTemplateWithParams(template.getContent(), requestDTO.getCustomParams());

                NotificationPayloadDTO payload = new NotificationPayloadDTO();
                payload.setTo(requestDTO.getTo());
                payload.setSubject(resolvedMessage);
                payload.setSnapshotConfig(config);
                payload.setSnapshotTemplate(template);

                String jsonPayload = JsonUtil.toJsonWithJavaTime(payload);
                messageProducer.sendMessage(smsQueueName, jsonPayload, false);

                return ResponseEntity.ok("✅ SMS request processed successfully.");
            });
        });
    }

    @PostMapping("/send-whatsapp")
    public Mono<ResponseEntity<String>> sendWhatsApp(@Valid @RequestBody NotificationRequestDTO requestDTO) {
        log.info("📲 Received WhatsApp send request with configId={}, templateId={}",
                requestDTO.getNotificationConfigId(), requestDTO.getTemplateId());

        if (!rateLimiterService.isAllowed(whatsappQueueName)) {
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("🚫 Rate limit exceeded for email channel"));
        }

        return Mono.zip(
                configService.findById(requestDTO.getNotificationConfigId()),
                templateService.getTemplateById(requestDTO.getTemplateId())
        ).flatMap(tuple -> {
            NotificationConfig config = tuple.getT1();
            TemplateEntity template = tuple.getT2();

            if (config == null || !config.isActive()) {
                return Mono.just(ResponseEntity.badRequest().body("❌ Invalid or inactive NotificationConfig ID"));
            }
            if (template == null) {
                return Mono.just(ResponseEntity.badRequest().body("❌ Invalid Template ID: Template not found"));
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
                return Mono.just(ResponseEntity.ok("✅ Scheduled WhatsApp message stored."));
            }

            return Mono.fromCallable(() -> {
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

                return ResponseEntity.ok("✅ WhatsApp request processed successfully.");
            }).onErrorResume(e -> {
                log.error("❌ Failed to serialize or upload WhatsApp payload", e);
                return Mono.just(ResponseEntity.internalServerError().body("❌ Failed to send WhatsApp notification"));
            });
        });
    }

    @PostMapping("/send-push")
    public Mono<ResponseEntity<String>> sendPushNotification(@Valid @RequestBody NotificationRequestDTO requestDTO) {
        log.info("📳 Received Push Notification send request with configId={}, templateId={}",
                requestDTO.getNotificationConfigId(), requestDTO.getTemplateId());

        if (!rateLimiterService.isAllowed(pushQueueName)) {
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("🚫 Rate limit exceeded for email channel"));
        }

        return Mono.zip(
                configService.findById(requestDTO.getNotificationConfigId()),
                templateService.getTemplateById(requestDTO.getTemplateId())
        ).flatMap(tuple -> {
            NotificationConfig config = tuple.getT1();
            TemplateEntity template = tuple.getT2();

            if (!config.isActive()) {
                return Mono.just(ResponseEntity.badRequest().body("❌ Invalid or inactive NotificationConfig ID"));
            }

            if (requestDTO.isScheduled()) {
                ScheduledNotification scheduled = ScheduledNotification.builder()
                        .notificationConfig(config)
                        .template(template)
                        .to(requestDTO.getTo())
                        .customParams(requestDTO.getCustomParams())
                        .queueName(pushQueueName)
                        .scheduleCron(requestDTO.getScheduleCron())
                        .active(true)
                        .build();
                return scheduledNotificationService.saveScheduledNotification(scheduled)
                        .thenReturn(ResponseEntity.ok("✅ Scheduled push notification stored."));
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

            return Mono.just(ResponseEntity.ok("✅ Push notification request queued successfully."));
        }).onErrorResume(e -> {
            log.error("❌ Failed to process push notification", e);
            return Mono.just(ResponseEntity.internalServerError().body("❌ Failed to process push notification"));
        });
    }

    @PostMapping("/send-voice")
    public Mono<ResponseEntity<String>> sendVoiceNotification(@Valid @RequestBody NotificationRequestDTO requestDTO) {
        log.info("📞 Received voice call request with configId={}, templateId={}",
                requestDTO.getNotificationConfigId(), requestDTO.getTemplateId());

        if (!rateLimiterService.isAllowed(voiceQueueName)) {
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("🚫 Rate limit exceeded for email channel"));
        }

        return Mono.zip(
                configService.findById(requestDTO.getNotificationConfigId()),
                templateService.getTemplateById(requestDTO.getTemplateId())
        ).flatMap(tuple -> {
            NotificationConfig config = tuple.getT1();
            TemplateEntity template = tuple.getT2();

            if (!config.isActive()) {
                return Mono.just(ResponseEntity.badRequest().body("❌ Invalid or inactive NotificationConfig ID"));
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
                return scheduledNotificationService.saveScheduledNotification(scheduled)
                        .thenReturn(ResponseEntity.ok("✅ Scheduled voice call stored."));
            }

            return Mono.fromCallable(() -> {
                int maxInlineChars = maxInlineKb * 1024;
                String resolvedTwiML = TemplateUtil.resolveTemplateWithParams(template.getContent(), requestDTO.getCustomParams());

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

                return ResponseEntity.ok("✅ Voice call request processed successfully.");
            }).onErrorResume(e -> {
                log.error("❌ Failed to serialize or upload voice payload", e);
                return Mono.just(ResponseEntity.internalServerError().body("❌ Failed to send voice call"));
            });
        }).onErrorResume(e -> {
            log.error("❌ Failed to process voice notification", e);
            return Mono.just(ResponseEntity.internalServerError().body("❌ Failed to process voice call"));
        });
    }


    @PostMapping("/send-webhook")
    public Mono<ResponseEntity<String>> sendWebhook(@Valid @RequestBody NotificationRequestDTO requestDTO) {
        log.info("🌐 Webhook request received: configId={}, templateId={}",
                requestDTO.getNotificationConfigId(), requestDTO.getTemplateId());

        return Mono.zip(
                configService.findById(requestDTO.getNotificationConfigId()),
                templateService.getTemplateById(requestDTO.getTemplateId())
        ).flatMap(tuple -> {
            NotificationConfig config = tuple.getT1();
            TemplateEntity template = tuple.getT2();

            if (!config.isActive()) {
                return Mono.just(ResponseEntity.badRequest().body("❌ Invalid or inactive NotificationConfig ID"));
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
                return scheduledNotificationService.saveScheduledNotification(scheduled)
                        .thenReturn(ResponseEntity.ok("✅ Scheduled webhook stored."));
            }

            String resolvedJson = TemplateUtil.resolveTemplateWithParams(template.getContent(), requestDTO.getCustomParams());
            template.setContent(resolvedJson);

            NotificationPayloadDTO payload = new NotificationPayloadDTO();
            payload.setTo(requestDTO.getTo());
            payload.setSnapshotConfig(config);
            payload.setSnapshotTemplate(template);

            String jsonPayload = JsonUtil.toJsonWithJavaTime(payload);
            messageProducer.sendMessage(webhookQueueName, jsonPayload, false);

            return Mono.just(ResponseEntity.ok("✅ Webhook queued successfully."));
        }).onErrorResume(e -> {
            log.error("❌ Failed to send webhook", e);
            return Mono.just(ResponseEntity.internalServerError().body("❌ Failed to send webhook"));
        });
    }

    @PostMapping("/send-queue")
    public Mono<ResponseEntity<String>> sendToQueue(@Valid @RequestBody NotificationRequestDTO requestDTO) {
        log.info("📤 Received send-queue request with configId={}, templateId={}, queue={}",
                requestDTO.getNotificationConfigId(), requestDTO.getTemplateId(), requestDTO.getTo());

        if (!rateLimiterService.isAllowed(publishQueueName)) {
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("🚫 Rate limit exceeded for queue: " + publishQueueName));
        }

        return Mono.zip(
                configService.findById(requestDTO.getNotificationConfigId()),
                templateService.getTemplateById(requestDTO.getTemplateId())
        ).flatMap(tuple -> {
            NotificationConfig config = tuple.getT1();
            TemplateEntity template = tuple.getT2();

            if (!config.isActive()) {
                return Mono.just(ResponseEntity.badRequest().body("❌ Invalid or inactive NotificationConfig ID"));
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
                return scheduledNotificationService.saveScheduledNotification(scheduled)
                        .thenReturn(ResponseEntity.ok("✅ Scheduled notification saved for future dispatch."));
            }

            return Mono.fromCallable(() -> {
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

                return ResponseEntity.ok("✅ Message published to queue: " + requestDTO.getTo());
            }).onErrorResume(e -> {
                log.error("❌ Failed to process and send message", e);
                return Mono.just(ResponseEntity.internalServerError().body("❌ Notification failed: " + e.getMessage()));
            });
        }).onErrorResume(e -> {
            log.error("❌ Failed to send queued message", e);
            return Mono.just(ResponseEntity.internalServerError().body("❌ Failed to send queued message"));
        });
    }

}
