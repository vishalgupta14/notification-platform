package com.message.engine.service.webhook;

import com.message.engine.service.FailedWebhookLogService;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.model.FailedWebhookLog;
import com.notification.common.model.NotificationConfig;
import com.notification.common.repository.NotificationConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookSendService {

    private final NotificationConfigRepository configRepository;
    private final FailedWebhookLogService failedWebhookLogService;
    private final WebhookSenderFactory senderFactory;

    @Value("${notification.webhook.enabled:true}")
    private boolean isWebhookEnabled;

    public Mono<Void> sendWebhook(NotificationPayloadDTO request) {
        long start = System.currentTimeMillis();
        NotificationConfig config = request.getSnapshotConfig();
        String to = request.getTo();
        String message = request.getSnapshotTemplate().getContent();

        if (!isWebhookEnabled) {
            log.info("[WEBHOOK-SIMULATION] Disabled via config");
            return Mono.empty();
        }

        return trySend(config, to, message)
                .flatMap(success -> {
                    if (success) return Mono.empty();

                    return handleFallbacks(config, to, message, request)
                            .flatMap(fallbackSuccess -> {
                                if (fallbackSuccess) return Mono.empty();
                                return logFailure(request, "All fallback attempts failed.");
                            });
                })
                .doFinally(signal -> {
                    long duration = System.currentTimeMillis() - start;
                    log.info("⏱️ Webhook send process took {} ms", duration);
                });
    }

    private Mono<Boolean> trySend(NotificationConfig config, String to, String message) {
        return Mono.fromCallable(() -> {
            WebhookSender sender = senderFactory.getSender(config.getProvider().toLowerCase());
            sender.sendWebhook(config.getConfig(), to, message);
            log.info("✅ Webhook sent to {} using {}", to, config.getProvider());
            return true;
        }).onErrorResume(e -> {
            log.warn("⚠️ Webhook attempt failed [{}]: {}", config.getProvider(), e.getMessage());
            return Mono.just(false);
        });
    }

    private Mono<Boolean> handleFallbacks(NotificationConfig config, String to, String message, NotificationPayloadDTO request) {
        if (StringUtils.isNotBlank(config.getFallbackConfigId())) {
            return configRepository.findById(config.getFallbackConfigId())
                    .flatMap(fallback -> trySend(fallback, to, message))
                    .defaultIfEmpty(false);
        }

        if (config.getPrivacyFallbackConfig() != null && !config.getPrivacyFallbackConfig().isEmpty()) {
            NotificationConfig dynamic = new NotificationConfig();
            dynamic.setConfig(config.getPrivacyFallbackConfig());
            dynamic.setClientName(config.getClientName());
            dynamic.setProvider((String) config.getPrivacyFallbackConfig().get("provider"));
            dynamic.setChannel(config.getChannel());
            dynamic.setActive(true);
            return trySend(dynamic, to, message);
        }

        return Mono.just(false);
    }

    private Mono<Void> logFailure(NotificationPayloadDTO request, String error) {
        FailedWebhookLog logEntry = FailedWebhookLog.builder()
                .notificationConfigId(request.getSnapshotConfig().getId())
                .templateId(request.getSnapshotTemplate().getId())
                .webhookUrl(request.getTo())
                .message(request.getSnapshotTemplate().getContent())
                .timestamp(System.currentTimeMillis())
                .errorMessage(error)
                .build();

         failedWebhookLogService.save(logEntry);
         return Mono.empty();
    }
}
