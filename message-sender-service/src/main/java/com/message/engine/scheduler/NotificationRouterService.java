package com.message.engine.scheduler;

import com.message.engine.service.email.EmailSendService;
import com.message.engine.service.notification.PushNotificationSendService;
import com.message.engine.service.sms.SmsSendService;
import com.message.engine.service.voice.VoiceSendService;
import com.message.engine.service.webhook.WebhookSendService;
import com.message.engine.service.whatsapp.WhatsAppSendService;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.repository.UnsentMessageRepository;
import com.notification.common.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRouterService {

    private final EmailSendService emailSendService;
    private final SmsSendService smsSendService;
    private final WhatsAppSendService whatsAppSendService;
    private final PushNotificationSendService pushNotificationSendService;
    private final VoiceSendService voiceSendService;
    private final WebhookSendService webhookSendService;
    private final UnsentMessageRepository repository;

    @Value("${email.queue.name}")
    private String emailQueue;

    @Value("${sms.queue.name}")
    private String smsQueue;

    @Value("${whatsapp.queue.name}")
    private String whatsappQueue;

    @Value("${push.queue.name}")
    private String pushQueue;

    @Value("${voice.queue.name}")
    private String voiceQueue;

    @Value("${webhook.queue.name}")
    private String webhookQueue;

    @Scheduled(fixedDelayString = "${unsent.retry.interval-ms:6000}")
    public void retryUnsentMessages() {
        log.info("⏳ Starting retry of unsent messages...");

        repository.findAll()
                .publishOn(Schedulers.boundedElastic())
                .flatMap(unsent -> {
                    log.info("📩 Retrying unsent message for queue [{}]: {}", unsent.getQueueName(), unsent.getMessage());
                    NotificationPayloadDTO payload = JsonUtil.fromJsonWithJavaTime(
                            unsent.getMessage(), NotificationPayloadDTO.class);
                    return Mono.fromRunnable(() -> route(unsent.getQueueName(), payload))
                            .then(repository.deleteById(unsent.getId()))
                            .doOnSuccess(v -> log.info("✅ Retried and removed unsent message with id {}", unsent.getId()))
                            .onErrorResume(e -> {
                                log.error("❌ Retry failed for message id {}: {}", unsent.getId(), e.getMessage(), e);
                                return Mono.empty();
                            });
                })
                .doOnComplete(() -> log.info("✅ Completed retry cycle for unsent messages."))
                .subscribe();
    }

    public void route(String queue, NotificationPayloadDTO payload) {
        try {
            if (queue.equals(emailQueue)) {
                emailSendService.sendEmail(payload).subscribe();
            } else if (queue.equals(smsQueue)) {
                smsSendService.sendSms(payload).subscribe();
            } else if (queue.equals(whatsappQueue)) {
                whatsAppSendService.sendWhatsApp(payload).subscribe();
            } else if (queue.equals(pushQueue)) {
                pushNotificationSendService.sendPush(payload).subscribe();
            } else if (queue.equals(voiceQueue)) {
                voiceSendService.sendVoice(payload).subscribe();
            } else if (queue.equals(webhookQueue)) {
                webhookSendService.sendWebhook(payload).subscribe();
            } else {
                log.warn("❓ Unsupported queue name: {}", queue);
            }
        } catch (Exception e) {
            log.error("❌ Error routing message for queue {}: {}", queue, e.getMessage(), e);
        }
    }
}