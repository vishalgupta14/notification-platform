package com.message.engine.service.voice;

import com.message.engine.service.FailedVoiceLogService;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.model.FailedVoiceLog;
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
public class VoiceSendService {

    private static final Logger log = LoggerFactory.getLogger(VoiceSendService.class);

    private final VoiceSenderFactory voiceSenderFactory;
    private final NotificationConfigRepository configRepository;
    private final FailedVoiceLogService failedVoiceLogService;

    @Value("${notification.voice.enabled:true}")
    private boolean isVoiceSendingEnabled;

    public void sendVoice(NotificationPayloadDTO request) {
        long start = System.currentTimeMillis();
        String configId = request.getSnapshotConfig().getId();
        String templateId = request.getSnapshotTemplate().getId();
        String phoneNumber = request.getTo();
        String voiceXml = request.getSnapshotTemplate().getContent(); // assumed to be TwiML

        NotificationConfig mainConfig = request.getSnapshotConfig();

        try {
            if (!isVoiceSendingEnabled) {
                log.info("[VOICE-SIMULATION] Voice sending is disabled.");
                return;
            }

            if (trySend(mainConfig, phoneNumber, voiceXml)) return;

            if (StringUtils.isNotBlank(mainConfig.getFallbackConfigId())) {
                NotificationConfig fallback = configRepository.findById(mainConfig.getFallbackConfigId()).orElse(null);
                if (fallback != null && trySend(fallback, phoneNumber, voiceXml)) return;
            }

            if (mainConfig.getPrivacyFallbackConfig() != null && !mainConfig.getPrivacyFallbackConfig().isEmpty()) {
                NotificationConfig dynamicConfig = new NotificationConfig();
                dynamicConfig.setConfig(mainConfig.getPrivacyFallbackConfig());
                dynamicConfig.setProvider((String) mainConfig.getPrivacyFallbackConfig().get("provider"));
                dynamicConfig.setClientName(mainConfig.getClientName());
                dynamicConfig.setChannel(mainConfig.getChannel());
                dynamicConfig.setActive(true);

                if (trySend(dynamicConfig, phoneNumber, voiceXml)) return;
            }

            log.error("❌ Voice call failed for {}. All configs exhausted.", phoneNumber);
            failedVoiceLogService.save(FailedVoiceLog.builder()
                    .phoneNumber(phoneNumber)
                    .message(voiceXml)
                    .templateId(templateId)
                    .notificationConfigId(configId)
                    .timestamp(System.currentTimeMillis())
                    .errorMessage("All fallback attempts failed.")
                    .build());

            throw new RuntimeException("Voice delivery failed");

        } catch (Exception e) {
            log.error("🚨 Error during voice delivery: {}", e.getMessage(), e);
            throw e;
        } finally {
            long time = System.currentTimeMillis() - start;
            log.info("⏱️ Voice delivery completed in {} ms", time);
        }
    }

    private boolean trySend(NotificationConfig config, String to, String xml) {
        try {
            VoiceSender sender = voiceSenderFactory.getSender(config.getProvider().toLowerCase()+"-voice");
            sender.sendVoice(config.getConfig(), to, xml);
            log.info("✅ Voice call placed to {} using provider {}", to, config.getProvider());
            return true;
        } catch (Exception e) {
            log.warn("⚠️ Voice sending failed using provider [{}]: {}", config.getProvider(), e.getMessage());
            return false;
        }
    }
}
