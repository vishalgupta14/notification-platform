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
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

@Service("artemisMessageProducer")
@ConditionalOnExpression("'${messaging.mode}'=='activemq' or '${messaging.mode}'=='both'")
@ConditionalOnClass(name = "org.springframework.jms.core.JmsTemplate")
public class ArtemisMessageProducer implements MessageProducer {

    private static final Logger log = LoggerFactory.getLogger(ArtemisMessageProducer.class);
    private final JmsTemplate jmsTemplate;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final UnsentMessageRepository unsentRepo;

    @Autowired
    public ArtemisMessageProducer(JmsTemplate jmsTemplate,
                                  RetryRegistry retryRegistry,
                                  CircuitBreakerRegistry circuitBreakerRegistry,
                                  UnsentMessageRepository unsentRepo) {
        this.jmsTemplate = jmsTemplate;
        this.retry = retryRegistry.retry("artemis-retry");
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("artemis-cb");
        this.unsentRepo = unsentRepo;
    }

    @Override
    public void sendMessage(String queueName, String message, boolean isPubSub) {
        jmsTemplate.setPubSubDomain(isPubSub);
        Runnable sendLogic = () -> jmsTemplate.convertAndSend(queueName, message);

        try {
            Retry.decorateRunnable(retry,
                    CircuitBreaker.decorateRunnable(circuitBreaker, sendLogic)
            ).run();
            log.info("[Artemis] Sent: " + message);
        } catch (Exception e) {
            log.error("[Artemis] Failed after retries, saving to DB");
            UnsentMessage fallbackMsg = new UnsentMessage(queueName, message, MessagingMode.ACTIVEMQ);
            unsentRepo.save(fallbackMsg);
        }
    }
}
