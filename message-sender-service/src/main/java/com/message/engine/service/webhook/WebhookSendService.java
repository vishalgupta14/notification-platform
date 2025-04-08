package com.message.engine.service.webhook;

import com.message.engine.service.FailedWebhookLogService;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.model.FailedWebhookLog;
import com.notification.common.model.NotificationConfig;
import com.notification.common.repository.NotificationConfigRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WebhookSendService {

    private static final Logger log = LoggerFactory.getLogger(WebhookSendService.class);

    private final NotificationConfigRepository configRepository;
    private final FailedWebhookLogService failedWebhookLogService;
    private final WebhookSenderFactory senderFactory;

    @Value("${notification.webhook.enabled:true}")
    private boolean isWebhookEnabled;

    public void sendWebhook(NotificationPayloadDTO request) {
        long start = System.currentTimeMillis();
        NotificationConfig config = request.getSnapshotConfig();
        String messageBody = request.getSnapshotTemplate().getContent();
        String to = request.getTo();

        try {
            if (!isWebhookEnabled) {
                log.info("[WEBHOOK-SIMULATION] Disabled via config");
                return;
            }

            if (trySend(config, to, messageBody)) return;

            if (StringUtils.isNotBlank(config.getFallbackConfigId())) {
                NotificationConfig fallback = configRepository.findById(config.getFallbackConfigId()).orElse(null);
                if (fallback != null && trySend(fallback, to, messageBody)) return;
            }

            if (config.getPrivacyFallbackConfig() != null && !config.getPrivacyFallbackConfig().isEmpty()) {
                NotificationConfig dynamic = new NotificationConfig();
                dynamic.setConfig(config.getPrivacyFallbackConfig());
                dynamic.setClientName(config.getClientName());
                dynamic.setProvider((String) config.getPrivacyFallbackConfig().get("provider"));
                dynamic.setChannel(config.getChannel());
                dynamic.setActive(true);

                if (trySend(dynamic, to, messageBody)) return;
            }

            failedWebhookLogService.save(FailedWebhookLog.builder()
                    .notificationConfigId(config.getId())
                    .templateId(request.getSnapshotTemplate().getId())
                    .webhookUrl(to)
                    .message(messageBody)
                    .timestamp(System.currentTimeMillis())
                    .errorMessage("All fallback attempts failed.")
                    .build());

        } catch (Exception e) {
            log.error("❌ Webhook sending failed", e);
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - start;
            log.info("⏱️ Webhook send process took {} ms", duration);
        }
    }

    private boolean trySend(NotificationConfig config, String to, String message) {
        try {
            WebhookSender sender = senderFactory.getSender(config.getProvider().toLowerCase());
            sender.sendWebhook(config.getConfig(), to, message);
            log.info("✅ Webhook sent to {} using {}", to, config.getProvider());
            return true;
        } catch (Exception e) {
            log.warn("⚠️ Webhook attempt failed [{}]: {}", config.getProvider(), e.getMessage());
            return false;
        }
    }
}
