package com.message.engine.consumer.whatsapp;

import com.message.engine.service.whatsapp.WhatsAppSendService;
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
        " and '${whatsapp.enabled}'=='true'"
)
public class WhatsAppArtemisMessageListener {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppArtemisMessageListener.class);

    private final ThreadPoolTaskExecutor taskExecutor;
    private final WhatsAppSendService whatsAppSendService;

    public WhatsAppArtemisMessageListener(
            @Qualifier("taskExecutor") ThreadPoolTaskExecutor taskExecutor,
            WhatsAppSendService whatsAppSendService) {
        this.taskExecutor = taskExecutor;
        this.whatsAppSendService = whatsAppSendService;
    }

    @JmsListener(destination = "${whatsapp.queue.name}", containerFactory = "queueListenerFactory")
    public void listenWhatsAppQueue(String message) {
        log.info("[Artemis] Consumed WhatsApp message: {}", message);

        taskExecutor.submit(() -> {
            try {
                NotificationPayloadDTO request = JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class);
                whatsAppSendService.sendWhatsApp(request);
            } catch (Exception e) {
                log.error("❌ Failed to process WhatsApp message", e);
            }
        });
    }
}
