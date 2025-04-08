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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationSendService {

    private final NotificationConfigRepository configRepository;
    private final FailedPushLogService failedPushLogService;
    private final FcmTokenService fcmTokenService;

    @Value("${notification.push.enabled:true}")
    private boolean isPushEnabled;

    public void sendPush(NotificationPayloadDTO request) {
        String configId = request.getSnapshotConfig().getId();
        String templateId = request.getSnapshotTemplate().getId();
        String emailOrPhone = request.getTo(); // can be email or phone
        String title = "📢 Notification";
        String messageBody = request.getSnapshotTemplate().getContent();

        NotificationConfig mainConfig = request.getSnapshotConfig();

        if (!isPushEnabled) {
            log.info("[PUSH-SIMULATION] Push sending is DISABLED via config");
            return;
        }

        try {
            String fcmToken = fcmTokenService.resolveFcmToken(emailOrPhone); // <- Your logic here
            if (StringUtils.isBlank(fcmToken)) {
                throw new IllegalStateException("FCM token not found for user: " + emailOrPhone);
            }

            if (trySend(mainConfig, fcmToken, title, messageBody)) return;

            if (StringUtils.isNotBlank(mainConfig.getFallbackConfigId())) {
                NotificationConfig fallback = configRepository.findById(mainConfig.getFallbackConfigId()).orElse(null);
                if (fallback != null && trySend(fallback, fcmToken, title, messageBody)) return;
            }

            if (mainConfig.getPrivacyFallbackConfig() != null && !mainConfig.getPrivacyFallbackConfig().isEmpty()) {
                NotificationConfig fallback = new NotificationConfig();
                fallback.setConfig(mainConfig.getPrivacyFallbackConfig());
                fallback.setClientName(mainConfig.getClientName());
                fallback.setChannel(mainConfig.getChannel());
                fallback.setProvider(mainConfig.getProvider());
                fallback.setActive(true);

                if (trySend(fallback, fcmToken, title, messageBody)) return;
            }

            log.error("❌ Push notification failed for all fallback attempts.");
            failedPushLogService.save(FailedPushLog.builder()
                    .fcmToken(fcmToken)
                    .message(messageBody)
                    .notificationConfigId(configId)
                    .templateId(templateId)
                    .errorMessage("All fallback attempts failed")
                    .timestamp(System.currentTimeMillis())
                    .build());

        } catch (Exception e) {
            log.error("❌ Exception occurred while sending push notification", e);
            throw new RuntimeException(e);
        }
    }

    private boolean trySend(NotificationConfig config, String fcmToken, String title, String body) {
        try {
            Map<String, Object> configMap = config.getConfig();

            Object firebaseJsonRaw = configMap.get("firebaseJson");
            if (firebaseJsonRaw == null) {
                log.warn("❌ Missing firebaseJson in config: {}", config.getId());
                return false;
            }

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> firebaseJson = mapper.convertValue(firebaseJsonRaw, new TypeReference<>() {
            });

            String json = mapper.writeValueAsString(firebaseJson);
            InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

            String appName = config.getClientName();

            Map<String, Object> projectInfo = mapper.convertValue(firebaseJson.get("project_info"), new TypeReference<>() {
            });

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(stream))
                    .setProjectId((String) projectInfo.get("project_id"))
                    .build();

            FirebaseApp firebaseApp = FirebaseApp.initializeApp(options, appName);

            Notification notification = Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build();

            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(notification)
                    .build();

            String response = FirebaseMessaging.getInstance(firebaseApp).send(message);
            log.info("✅ Push notification sent to [{}], response: {}", fcmToken, response);
            return true;

        } catch (Exception e) {
            log.warn("⚠️ Failed to send push using config [{}]: {}", config.getClientName(), e.getMessage(), e);
            return false;
        }
    }
}
