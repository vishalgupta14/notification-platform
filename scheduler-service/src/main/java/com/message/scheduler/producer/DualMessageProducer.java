package com.message.scheduler.producer;

import com.notification.common.repository.UnsentMessageRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service("dualMessageProducer")
@ConditionalOnProperty(name = "messaging.mode", havingValue = "both")
@Primary
public class DualMessageProducer implements MessageProducer {

    private final KafkaMessageProducer kafkaProducer;
    private final ArtemisMessageProducer artemisProducer;
    private final UnsentMessageRepository unsentRepo;

    public DualMessageProducer(KafkaMessageProducer kafkaProducer,
                               ArtemisMessageProducer artemisProducer,
                               UnsentMessageRepository unsentRepo) {
        this.kafkaProducer = kafkaProducer;
        this.artemisProducer = artemisProducer;
        this.unsentRepo = unsentRepo;
    }

    @Override
    public void sendMessage(String queueName, String message, boolean isPubSub) {
        kafkaProducer.sendMessage(queueName, message, isPubSub);
        artemisProducer.sendMessage(queueName, message, isPubSub);
    }

}
