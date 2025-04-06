package com.message.engine.consumer.sms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.engine.service.sms.SmsSendService;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.utils.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression(
        "'${messaging.mode}'=='kafka' or '${messaging.mode}'=='both'" +
        " and '${sms.enabled}'=='true'"
)
public class SmsKafkaMessageListener {

    private static final Logger log = LoggerFactory.getLogger(SmsKafkaMessageListener.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ThreadPoolTaskExecutor smsTaskExecutor;
    private final SmsSendService smsSendService;

    public SmsKafkaMessageListener(
            @Qualifier("taskExecutor") ThreadPoolTaskExecutor smsTaskExecutor,
            SmsSendService smsSendService) {
        this.smsTaskExecutor = smsTaskExecutor;
        this.smsSendService = smsSendService;
    }

    @KafkaListener(topics = "${sms.queue.name}", groupId = "sms-consumer-group")
    public void listenSmsQueue(String message) {
        log.info("[Kafka] Consumed SMS message: {}", message);

        smsTaskExecutor.submit(() -> {
            try {
                NotificationPayloadDTO request = JsonUtil.fromJsonWithJavaTime(message, NotificationPayloadDTO.class);
                smsSendService.sendSms(request);
            } catch (Exception e) {
                log.error("❌ Failed to process SMS message", e);
            }
        });
    }
}
