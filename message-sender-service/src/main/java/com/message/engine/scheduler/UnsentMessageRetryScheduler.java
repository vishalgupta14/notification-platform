package com.message.engine.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.engine.service.email.EmailSendService;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.model.UnsentMessage;
import com.notification.common.repository.UnsentMessageRepository;
import com.notification.common.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
public class UnsentMessageRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(UnsentMessageRetryScheduler.class);

    private final UnsentMessageRepository repository;
    private final EmailSendService emailSendService;
    private final ObjectMapper objectMapper;

    private final ExecutorService emailExecutor = Executors.newFixedThreadPool(5);

    @Value("${unsent.retry.enabled:false}")
    private boolean retryEnabled;

    @Value("${unsent.retry.interval-ms:6000}")
    private long retryInterval;

    @Value("${unsent.retry.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${unsent.retry.interval-ms}")
    public void retryUnsentMessages() {
        if (!retryEnabled) return;

        int page = 0;

        while (true) {
            Page<UnsentMessage> messagePage = repository.findAll(PageRequest.of(page, batchSize));
            List<UnsentMessage> messages = messagePage.getContent();

            if (messages.isEmpty()) break;

            for (UnsentMessage message : messages) {
                emailExecutor.submit(() -> {
                    try {
                        log.info("📩 Retrying unsent message: {}", message);
                        NotificationPayloadDTO request = JsonUtil.fromJsonWithJavaTime(
                                message.getMessage(), NotificationPayloadDTO.class);

                        emailSendService.sendEmail(request);
                        repository.deleteById(message.getId());

                        log.info("✅ Retried and removed unsent message with id {}", message.getId());
                    } catch (Exception e) {
                        log.error("❌ Retry failed for message id {}: {}", message.getId(), e.getMessage(), e);
                    }
                });
            }

            if (!messagePage.hasNext()) break;
            page++;
        }
    }

}
