package com.message.engine.service.sms;

import com.message.engine.manager.SmsConnectionPoolManager;
import com.message.engine.service.FailedSmsLogService;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.model.FailedSmsLog;
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
public class SmsSendService {

    private final NotificationConfigRepository configRepository;
    private final FailedSmsLogService failedSmsLogService;
    private final SmsSenderFactory smsSenderFactory;
    private final SmsConnectionPoolManager smsConnectionPoolManager;

    @Value("${notification.sms.enabled:true}")
    private boolean isSmsSendingEnabled;

    public Mono<Void> sendSms(NotificationPayloadDTO request) {
        long start = System.currentTimeMillis();
        String phone = request.getTo();
        String message = request.getSnapshotTemplate().getContent();
        NotificationConfig config = request.getSnapshotConfig();

        if (!isSmsSendingEnabled) {
            log.info("[SMS-SIMULATION] SMS sending disabled via config.");
            return Mono.empty();
        }

        return trySend(config, phone, message)
                .flatMap(success -> {
                    if (success) return Mono.empty();

                    return handleFallbacks(config, phone, message, request)
                            .flatMap(fallbackSuccess -> {
                                if (fallbackSuccess) return Mono.empty();
                                return logFailure(request, "All fallback attempts failed.");
                            });
                })
                .doFinally(signal -> {
                    long duration = System.currentTimeMillis() - start;
                    log.info("⏱️ SMS send process took {} ms", duration);
                });
    }

    private Mono<Boolean> trySend(NotificationConfig config, String to, String text) {
        return Mono.fromCallable(() -> {
            Map<String, Object> resolvedConfig = config.getConfig();
            SmsSender sender = smsSenderFactory.getSender(config.getProvider().toLowerCase());
            sender.sendSms(resolvedConfig, to, text);
            log.info("✅ SMS sent to {} using provider {}", to, config.getProvider());
            return true;
        }).onErrorResume(e -> {
            log.warn("⚠️ SMS send failed [{}]: {}", config.getProvider(), e.getMessage());
            return Mono.just(false);
        });
    }

    private Mono<Boolean> handleFallbacks(NotificationConfig mainConfig, String to, String message, NotificationPayloadDTO request) {
        if (StringUtils.isNotBlank(mainConfig.getFallbackConfigId())) {
            return configRepository.findById(mainConfig.getFallbackConfigId())
                    .flatMap(fallback -> trySend(fallback, to, message))
                    .defaultIfEmpty(false);
        }

        if (mainConfig.getPrivacyFallbackConfig() != null && !mainConfig.getPrivacyFallbackConfig().isEmpty()) {
            NotificationConfig dynamic = new NotificationConfig();
            dynamic.setConfig(mainConfig.getPrivacyFallbackConfig());
            dynamic.setClientName(mainConfig.getClientName());
            dynamic.setProvider((String) mainConfig.getPrivacyFallbackConfig().get("provider"));
            dynamic.setChannel(mainConfig.getChannel());
            dynamic.setActive(true);
            return trySend(dynamic, to, message);
        }

        return Mono.just(false);
    }

    private Mono<Void> logFailure(NotificationPayloadDTO request, String reason) {
        FailedSmsLog logEntry = FailedSmsLog.builder()
                .phoneNumber(request.getTo())
                .message(request.getSnapshotTemplate().getContent())
                .templateId(request.getSnapshotTemplate().getId())
                .notificationConfigId(request.getSnapshotConfig().getId())
                .timestamp(System.currentTimeMillis())
                .errorMessage(reason)
                .build();

         failedSmsLogService.save(logEntry);
         return Mono.empty();
    }
}
