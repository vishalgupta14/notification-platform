package com.message.engine.service.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import com.message.engine.service.FailedPushLogService;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.model.FailedPushLog;
import com.notification.common.model.NotificationConfig;
import com.notification.common.repository.NotificationConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationSendService {

    private final NotificationConfigRepository configRepository;
    private final FailedPushLogService failedPushLogService;
    private final FcmTokenService fcmTokenService;

    @Value("${notification.push.enabled:true}")
    private boolean isPushEnabled;

    public Mono<Void> sendPush(NotificationPayloadDTO request) {
        if (!isPushEnabled) {
            log.info("[PUSH-SIMULATION] Push sending is DISABLED via config");
            return Mono.empty();
        }

        String configId = request.getSnapshotConfig().getId();
        String templateId = request.getSnapshotTemplate().getId();
        String userId = request.getTo(); // phone or email
        String title = "📢 Notification";
        String messageBody = request.getSnapshotTemplate().getContent();
        NotificationConfig mainConfig = request.getSnapshotConfig();

        return fcmTokenService.resolveFcmToken(userId)
                .flatMap(fcmToken -> trySend(mainConfig, fcmToken, title, messageBody)
                        .flatMap(success -> {
                            if (success) return Mono.empty(); // Success path
                            return fallbackSend(mainConfig, fcmToken, title, messageBody)
                                    .flatMap(fallbackSuccess -> {
                                        if (fallbackSuccess) return Mono.empty(); // Fallback success
                                        return handleFailure(fcmToken, configId, templateId, messageBody); // All failed
                                    });
                        }))
                .onErrorResume(e -> {
                    log.error("❌ Exception occurred while sending push notification", e);
                    return Mono.empty();
                });
    }

    private Mono<Boolean> trySend(NotificationConfig config, String fcmToken, String title, String body) {
        return Mono.fromCallable(() -> sendToFcm(config, fcmToken, title, body))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.warn("⚠️ Failed to send push using config [{}]: {}", config.getClientName(), e.getMessage());
                    return Mono.just(false);
                });
    }

    private Mono<Boolean> fallbackSend(NotificationConfig mainConfig, String fcmToken, String title, String body) {
        if (StringUtils.isNotBlank(mainConfig.getFallbackConfigId())) {
            return configRepository.findById(mainConfig.getFallbackConfigId())
                    .flatMap(fallbackConfig -> trySend(fallbackConfig, fcmToken, title, body))
                    .switchIfEmpty(tryPrivacyFallback(mainConfig, fcmToken, title, body));
        } else {
            return tryPrivacyFallback(mainConfig, fcmToken, title, body);
        }
    }



    private Mono<Boolean> tryPrivacyFallback(NotificationConfig mainConfig, String fcmToken, String title, String body) {
        Map<String, Object> privacyConfig = mainConfig.getPrivacyFallbackConfig();
        if (privacyConfig != null && !privacyConfig.isEmpty()) {
            NotificationConfig dynamic = new NotificationConfig();
            dynamic.setConfig(privacyConfig);
            dynamic.setClientName(mainConfig.getClientName());
            dynamic.setProvider(mainConfig.getProvider());
            dynamic.setChannel(mainConfig.getChannel());
            dynamic.setActive(true);
            return trySend(dynamic, fcmToken, title, body);
        }
        return Mono.just(false);
    }


    private Mono<Void> handleFailure(String fcmToken, String configId, String templateId, String messageBody) {
        return Mono.fromRunnable(() -> {
            log.error("❌ Push notification failed for all fallback attempts.");
            failedPushLogService.save(FailedPushLog.builder()
                    .fcmToken(fcmToken)
                    .message(messageBody)
                    .notificationConfigId(configId)
                    .templateId(templateId)
                    .errorMessage("All fallback attempts failed")
                    .timestamp(System.currentTimeMillis())
                    .build());
        }).then(); // Convert to Mono<Void>
    }

    private boolean sendToFcm(NotificationConfig config, String fcmToken, String title, String body) throws Exception {
        Map<String, Object> configMap = config.getConfig();
        Object firebaseJsonRaw = configMap.get("firebaseJson");
        if (firebaseJsonRaw == null) {
            throw new IllegalStateException("Missing firebaseJson config");
        }

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> firebaseJson = mapper.convertValue(firebaseJsonRaw, new TypeReference<>() {});
        String json = mapper.writeValueAsString(firebaseJson);
        InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> projectInfo = mapper.convertValue(firebaseJson.get("project_info"), new TypeReference<>() {});
        String projectId = (String) projectInfo.get("project_id");
        String appName = config.getClientName();

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(stream))
                .setProjectId(projectId)
                .build();

        FirebaseApp app;
        try {
            app = FirebaseApp.getInstance(appName);
        } catch (IllegalStateException e) {
            app = FirebaseApp.initializeApp(options, appName);
        }

        Notification notification = Notification.builder().setTitle(title).setBody(body).build();
        Message message = Message.builder().setToken(fcmToken).setNotification(notification).build();

        String response = FirebaseMessaging.getInstance(app).send(message);
        log.info("✅ Push notification sent to [{}], response: {}", fcmToken, response);
        return true;
    }
}
