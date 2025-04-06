package com.message.node.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(MessageProducer.class)
public class NoOpMessageProducer implements MessageProducer {

    private static final Logger log = LoggerFactory.getLogger(NoOpMessageProducer.class);

    @Override
    public void sendMessage(String queueName, String message, boolean isPubSub) {
        log.info("[WARNING] No valid messaging mode active or messaging dependencies missing. Message not sent: " + message);
    }
}
