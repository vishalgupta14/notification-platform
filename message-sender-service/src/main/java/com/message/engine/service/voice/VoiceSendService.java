package com.message.engine.service.voice;

import com.message.engine.service.FailedVoiceLogService;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.model.FailedVoiceLog;
import com.notification.common.model.NotificationConfig;
import com.notification.common.repository.NotificationConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceSendService {

    private final VoiceSenderFactory voiceSenderFactory;
    private final NotificationConfigRepository configRepository;
    private final FailedVoiceLogService failedVoiceLogService;

    @Value("${notification.voice.enabled:true}")
    private boolean isVoiceSendingEnabled;

    public Mono<Void> sendVoice(NotificationPayloadDTO request) {
        long start = System.currentTimeMillis();
        String phoneNumber = request.getTo();
        String voiceXml = request.getSnapshotTemplate().getContent();
        NotificationConfig mainConfig = request.getSnapshotConfig();

        if (!isVoiceSendingEnabled) {
            log.info("[VOICE-SIMULATION] Voice sending is disabled.");
            return Mono.empty();
        }

        return trySend(mainConfig, phoneNumber, voiceXml)
                .flatMap(success -> {
                    if (success) return Mono.empty();

                    return handleFallbacks(mainConfig, phoneNumber, voiceXml, request)
                            .flatMap(fallbackSuccess -> {
                                if (fallbackSuccess) return Mono.empty();
                                return logFailure(request, "All fallback attempts failed.");
                            });
                })
                .doFinally(signal -> {
                    long time = System.currentTimeMillis() - start;
                    log.info("⏱️ Voice delivery completed in {} ms", time);
                });
    }

    private Mono<Boolean> trySend(NotificationConfig config, String to, String xml) {
        return Mono.fromCallable(() -> {
            VoiceSender sender = voiceSenderFactory.getSender(config.getProvider().toLowerCase() + "-voice");
            sender.sendVoice(config.getConfig(), to, xml);
            log.info("✅ Voice call placed to {} using provider {}", to, config.getProvider());
            return true;
        }).onErrorResume(e -> {
            log.warn("⚠️ Voice sending failed using [{}]: {}", config.getProvider(), e.getMessage());
            return Mono.just(false);
        });
    }

    private Mono<Boolean> handleFallbacks(NotificationConfig mainConfig, String to, String xml, NotificationPayloadDTO request) {
        if (StringUtils.isNotBlank(mainConfig.getFallbackConfigId())) {
            return configRepository.findById(mainConfig.getFallbackConfigId())
                    .flatMap(fallback -> trySend(fallback, to, xml))
                    .defaultIfEmpty(false);
        }

        if (mainConfig.getPrivacyFallbackConfig() != null && !mainConfig.getPrivacyFallbackConfig().isEmpty()) {
            NotificationConfig dynamic = new NotificationConfig();
            dynamic.setConfig(mainConfig.getPrivacyFallbackConfig());
            dynamic.setClientName(mainConfig.getClientName());
            dynamic.setProvider((String) mainConfig.getPrivacyFallbackConfig().get("provider"));
            dynamic.setChannel(mainConfig.getChannel());
            dynamic.setActive(true);
            return trySend(dynamic, to, xml);
        }

        return Mono.just(false);
    }

    private Mono<Void> logFailure(NotificationPayloadDTO request, String errorMsg) {
        FailedVoiceLog logEntry = FailedVoiceLog.builder()
                .phoneNumber(request.getTo())
                .message(request.getSnapshotTemplate().getContent())
                .templateId(request.getSnapshotTemplate().getId())
                .notificationConfigId(request.getSnapshotConfig().getId())
                .timestamp(System.currentTimeMillis())
                .errorMessage(errorMsg)
                .build();

         failedVoiceLogService.save(logEntry);
         return Mono.empty();
    }
}
