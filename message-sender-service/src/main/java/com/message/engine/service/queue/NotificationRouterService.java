package com.message.engine.service.queue;

import com.message.engine.service.FailedQueueLogService;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.model.FailedQueueLog;
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
public class NotificationRouterService {

    private final MessagePublisherFactory publisherFactory;
    private final FailedQueueLogService failedQueueLogService;
    private final NotificationConfigRepository configRepository;

    @Value("${notification.queue.enabled:true}")
    private boolean isQueuePublishingEnabled;

    public Mono<Void> route(NotificationPayloadDTO request) {
        long startTime = System.currentTimeMillis();
        NotificationConfig config = request.getSnapshotConfig();
        String destination = request.getTo();
        String jsonPayload = request.getSnapshotTemplate().getContent();

        if (!isQueuePublishingEnabled) {
            log.info("[QUEUE-SIMULATION] Queue publishing is disabled.");
            return Mono.empty();
        }

        return tryPublish(config, destination, jsonPayload)
                .flatMap(success -> {
                    if (success) return Mono.empty();

                    return handleFallbacks(config, destination, jsonPayload, request)
                            .flatMap(fallbackSuccess -> {
                                if (fallbackSuccess) return Mono.empty();
                                return logFailure(config, request, destination, jsonPayload, "All fallback attempts failed.");
                            });
                })
                .doFinally(signal -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("⏱️ Queue publish process completed in {} ms", duration);
                });
    }

    private Mono<Boolean> tryPublish(NotificationConfig config, String destination, String payload) {
        return Mono.fromCallable(() -> {
            Map<String, Object> resolvedConfig = config.getConfig();
            publisherFactory.getPublisher(config.getProvider().toLowerCase())
                    .publish(resolvedConfig, destination, payload);
            log.info("✅ Published to [{}] using [{}]", destination, config.getProvider());
            return true;
        }).onErrorResume(e -> {
            log.warn("⚠️ Failed to publish with [{}]: {}", config.getProvider(), e.getMessage());
            return Mono.just(false);
        });
    }

    private Mono<Boolean> handleFallbacks(NotificationConfig mainConfig, String destination, String payload, NotificationPayloadDTO request) {
        if (StringUtils.isNotBlank(mainConfig.getFallbackConfigId())) {
            return configRepository.findById(mainConfig.getFallbackConfigId())
                    .flatMap(fallback -> tryPublish(fallback, destination, payload))
                    .defaultIfEmpty(false);
        }

        if (mainConfig.getPrivacyFallbackConfig() != null && !mainConfig.getPrivacyFallbackConfig().isEmpty()) {
            NotificationConfig dynamic = new NotificationConfig();
            dynamic.setConfig(mainConfig.getPrivacyFallbackConfig());
            dynamic.setClientName(mainConfig.getClientName());
            dynamic.setProvider((String) mainConfig.getPrivacyFallbackConfig().get("provider"));
            dynamic.setChannel(mainConfig.getChannel());
            dynamic.setActive(true);
            return tryPublish(dynamic, destination, payload);
        }

        return Mono.just(false);
    }

    private Mono<Void> logFailure(NotificationConfig config, NotificationPayloadDTO request, String destination, String payload, String reason) {
        FailedQueueLog logEntry = FailedQueueLog.builder()
                .notificationConfigId(config.getId())
                .templateId(request.getSnapshotTemplate().getId())
                .queueName(destination)
                .message(payload)
                .timestamp(System.currentTimeMillis())
                .errorMessage(reason)
                .build();

         failedQueueLogService.save(logEntry);
         return Mono.empty();
    }
}
