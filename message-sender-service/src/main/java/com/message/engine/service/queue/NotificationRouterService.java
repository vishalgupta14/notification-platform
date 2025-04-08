package com.message.engine.service.queue;

import com.message.engine.service.FailedQueueLogService;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.model.FailedQueueLog;
import com.notification.common.model.NotificationConfig;
import com.notification.common.repository.NotificationConfigRepository;
import com.notification.common.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationRouterService {

    private final MessagePublisherFactory publisherFactory;
    private final FailedQueueLogService failedQueueLogService;
    private final NotificationConfigRepository configRepository;

    @Value("${notification.queue.enabled:true}")
    private boolean isQueuePublishingEnabled;

    public void route(NotificationPayloadDTO request) {
        long startTime = System.currentTimeMillis();
        NotificationConfig config = request.getSnapshotConfig();
        String to = request.getTo();
        String jsonPayload =request.getSnapshotTemplate().getContent();

        try {
            if (!isQueuePublishingEnabled) {
                log.info("[QUEUE-SIMULATION] Queue publishing is DISABLED via configuration.");
                return;
            }

            if (tryPublish(config, to, jsonPayload)) return;

            if (StringUtils.isNotBlank(config.getFallbackConfigId())) {
                NotificationConfig fallback = configRepository.findById(config.getFallbackConfigId()).orElse(null);
                if (fallback != null && tryPublish(fallback, to, jsonPayload)) return;
            }

            if (config.getPrivacyFallbackConfig() != null && !config.getPrivacyFallbackConfig().isEmpty()) {
                NotificationConfig dynamic = new NotificationConfig();
                dynamic.setConfig(config.getPrivacyFallbackConfig());
                dynamic.setClientName(config.getClientName());
                dynamic.setProvider((String) config.getPrivacyFallbackConfig().get("provider"));
                dynamic.setChannel(config.getChannel());
                dynamic.setActive(true);

                if (tryPublish(dynamic, to, jsonPayload)) return;
            }

            failedQueueLogService.save(FailedQueueLog.builder()
                    .notificationConfigId(config.getId())
                    .templateId(request.getSnapshotTemplate().getId())
                    .queueName(to)
                    .message(jsonPayload)
                    .timestamp(System.currentTimeMillis())
                    .errorMessage("All fallback attempts failed.")
                    .build());

            log.error("❌ Failed to publish message to [{}]. All fallback options exhausted.", to);
            throw new RuntimeException("Queue publishing failed after all fallback attempts.");

        } catch (Exception e) {
            log.error("🚨 Exception occurred while publishing to queue [{}]: {}", to, e.getMessage(), e);
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("⏱️ Queue publish process completed in {} ms", duration);
        }
    }

    private boolean tryPublish(NotificationConfig config, String destination, String jsonPayload) {
        try {
            Map<String, Object> resolvedConfig = config.getConfig();
            publisherFactory.getPublisher(config.getProvider().toLowerCase())
                    .publish(resolvedConfig, destination, jsonPayload);

            log.info("✅ Message published to [{}] using provider [{}]", destination, config.getProvider());
            return true;
        } catch (Exception e) {
            log.warn("⚠️ Queue publish attempt failed with [{}]: {}", config.getProvider(), e.getMessage());
            return false;
        }
    }
}
