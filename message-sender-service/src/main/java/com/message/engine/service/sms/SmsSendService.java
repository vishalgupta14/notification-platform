package com.message.engine.service.sms;

import com.message.engine.manager.SmsConnectionPoolManager;
import com.message.engine.service.FailedSmsLogService;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.model.FailedSmsLog;
import com.notification.common.model.NotificationConfig;
import com.notification.common.repository.NotificationConfigRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class SmsSendService {

    private static final Logger log = LoggerFactory.getLogger(SmsSendService.class);

    private final NotificationConfigRepository configRepository;
    private final FailedSmsLogService failedSmsLogService;
    private final SmsSenderFactory smsSenderFactory;
    private final SmsConnectionPoolManager smsConnectionPoolManager;

    @Value("${notification.sms.enabled:true}")
    private boolean isSmsSendingEnabled;

    public void sendSms(NotificationPayloadDTO request) {
        long startTime = System.currentTimeMillis();
        String configId = request.getSnapshotConfig().getId();
        String templateId = request.getSnapshotTemplate().getId();
        String phoneNumber = request.getTo();
        String messageText = request.getSnapshotTemplate().getContent();

        NotificationConfig mainConfig = request.getSnapshotConfig();

        try {
            if (!isSmsSendingEnabled) {
                log.info("[SMS-SIMULATION] Skipping SMS sending (disabled via config).");
                return;
            }

            if (trySend(mainConfig, phoneNumber, messageText)) return;

            if (StringUtils.isNotBlank(mainConfig.getFallbackConfigId())) {
                NotificationConfig fallback = configRepository.findById(mainConfig.getFallbackConfigId()).orElse(null);
                if (fallback != null && trySend(fallback, phoneNumber, messageText)) return;
            }

            if (mainConfig.getPrivacyFallbackConfig() != null && !mainConfig.getPrivacyFallbackConfig().isEmpty()) {
                NotificationConfig dynamicConfig = new NotificationConfig();
                dynamicConfig.setConfig(mainConfig.getPrivacyFallbackConfig());
                dynamicConfig.setClientName(mainConfig.getClientName());
                dynamicConfig.setProvider((String) mainConfig.getPrivacyFallbackConfig().get("provider"));
                dynamicConfig.setChannel(mainConfig.getChannel());
                dynamicConfig.setActive(true);

                if (trySend(dynamicConfig, phoneNumber, messageText)) return;
            }

            log.error("❌ Failed to send SMS to {}. All attempts exhausted.", phoneNumber);
            failedSmsLogService.save(FailedSmsLog.builder()
                    .phoneNumber(phoneNumber)
                    .message(messageText)
                    .notificationConfigId(configId)
                    .templateId(templateId)
                    .timestamp(System.currentTimeMillis())
                    .errorMessage("All fallback attempts failed.")
                    .build());

            throw new RuntimeException("Failed to send SMS after all fallback attempts.");

        } catch (Exception e) {
            log.error("🚨 Exception occurred while sending SMS to {}: {}", phoneNumber, e.getMessage(), e);
            throw e; // optional: rethrow or wrap as needed
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("⏱️ SMS sending process completed in {} ms", duration);
        }
    }


    private boolean trySend(NotificationConfig config, String to, String text) {
        try {
            Map<String, Object> resolvedConfig = config.getConfig();
            SmsSender sender = smsSenderFactory.getSender(config.getProvider().toLowerCase());
            sender.sendSms(resolvedConfig, to, text);
            log.info("✅ SMS sent to {} using provider {}", to, config.getProvider());
            return true;
        } catch (Exception e) {
            log.warn("⚠️ SMS attempt failed with provider [{}]: {}", config.getProvider(), e.getMessage());
            return false;
        }
    }
}

