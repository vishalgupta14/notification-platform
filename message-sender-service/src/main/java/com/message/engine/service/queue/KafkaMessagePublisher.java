package com.message.engine.service.queue;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("kafka")
public class KafkaMessagePublisher implements MessagePublisher {

    private final KafkaProducerManager producerManager;

    public KafkaMessagePublisher(KafkaProducerManager producerManager) {
        this.producerManager = producerManager;
    }

    @Override
    public void publish(Map<String, Object> config, String topic, String payloadJson) {
        KafkaProducer<String, String> producer = producerManager.getOrCreateProducer(config);
        try {
            producer.send(new ProducerRecord<>(topic, payloadJson));
        } catch (Exception e) {
            throw new RuntimeException("❌ Kafka publish failed: " + e.getMessage(), e);
        }
    }
}
