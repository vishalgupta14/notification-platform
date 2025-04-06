package com.message.engine.consumer.email;

import com.message.engine.service.email.EmailSendService;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.utils.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression(
        "'${messaging.mode}'=='activemq' or '${messaging.mode}'=='both'" +
                " and '${email.enabled}'=='true'"
)
public class ArtemisMessageListener {

    private static final Logger log = LoggerFactory.getLogger(ArtemisMessageListener.class);

    private final ThreadPoolTaskExecutor emailTaskExecutor;
    private final EmailSendService emailSendService;

    public ArtemisMessageListener(@Qualifier("taskExecutor") ThreadPoolTaskExecutor emailTaskExecutor,
                              EmailSendService emailSendService) {
        this.emailTaskExecutor = emailTaskExecutor;
        this.emailSendService = emailSendService;
    }

    @JmsListener(destination = "${email.queue.name}", containerFactory = "queueListenerFactory")
    public void listenEmailQueue(String message) {
        log.info("[Artemis] Consumed message: {}", message);

        emailTaskExecutor.submit(() -> {
            try {
                NotificationPayloadDTO request = JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class);
                emailSendService.sendEmail(request);
            } catch (Exception e) {
                log.error("❌ Failed to process email message", e);
            }
        });
    }
}
