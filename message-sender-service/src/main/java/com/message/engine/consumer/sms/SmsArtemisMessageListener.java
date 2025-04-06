package com.message.engine.consumer.sms;

import com.message.engine.service.sms.SmsSendService;
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
        " and '${sms.enabled}'=='true'"
)
public class SmsArtemisMessageListener {

    private static final Logger log = LoggerFactory.getLogger(SmsArtemisMessageListener.class);

    private final ThreadPoolTaskExecutor taskExecutor;
    private final SmsSendService smsSendService;

    public SmsArtemisMessageListener(
            @Qualifier("taskExecutor") ThreadPoolTaskExecutor taskExecutor,
            SmsSendService smsSendService) {
        this.taskExecutor = taskExecutor;
        this.smsSendService = smsSendService;
    }

    @JmsListener(destination = "${sms.queue.name}", containerFactory = "queueListenerFactory")
    public void listenSmsQueue(String message) {
        log.info("[Artemis] Consumed SMS message: {}", message);

        taskExecutor.submit(() -> {
            try {
                NotificationPayloadDTO request = JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class);
                smsSendService.sendSms(request);
            } catch (Exception e) {
                log.error("❌ Failed to process SMS message", e);
            }
        });
    }
}
