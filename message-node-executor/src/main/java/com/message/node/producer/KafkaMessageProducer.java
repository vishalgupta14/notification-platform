package com.message.node.producer;

import com.notification.common.enums.MessagingMode;
import com.notification.common.model.UnsentMessage;
import com.notification.common.repository.UnsentMessageRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

@Service("kafkaMessageProducer")
@ConditionalOnExpression("'${messaging.mode}'=='kafka' or '${messaging.mode}'=='both'")
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
public class KafkaMessageProducer implements MessageProducer {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessageProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final UnsentMessageRepository unsentRepo;

    @Autowired
    public KafkaMessageProducer(KafkaTemplate<String, String> kafkaTemplate,
                                RetryRegistry retryRegistry,
                                CircuitBreakerRegistry circuitBreakerRegistry,
                                UnsentMessageRepository unsentRepo) {
        this.kafkaTemplate = kafkaTemplate;
        this.retry = retryRegistry.retry("kafka-retry");
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("kafka-cb");
        this.unsentRepo = unsentRepo;
    }

    @Override
    public void sendMessage(String queueName, String message, boolean isPubSub) {
        Runnable sendLogic = () -> {
            try {
                kafkaTemplate.send(queueName, message).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Kafka send failed", e);
            }
        };

        try {
            Retry.decorateRunnable(retry,
                    CircuitBreaker.decorateRunnable(circuitBreaker, sendLogic)
            ).run();
            log.info("[Kafka] Sent: " + message);
        } catch (Exception e) {
            log.error("[Kafka] Failed after retries, saving to DB");
            UnsentMessage fallbackMsg = new UnsentMessage(queueName, message, MessagingMode.KAFKA);
            unsentRepo.save(fallbackMsg);
        }
    }


}
