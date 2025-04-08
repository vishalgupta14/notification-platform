package com.message.engine.service.queue;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Properties;

@Component("kafka")
public class KafkaMessagePublisher implements MessagePublisher {

    @Override
    public void publish(Map<String, Object> config, String topic, String payloadJson) {
        Properties props = new Properties();


        props.put("bootstrap.servers", config.get("bootstrapServers"));
        props.put("client.id", config.getOrDefault("clientId", "default-client"));
        props.put("acks", config.getOrDefault("acks", "all"));

        String keySerializer = (String) config.getOrDefault(
                "key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        String valueSerializer = (String) config.getOrDefault(
                "value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        props.put("key.serializer", keySerializer);
        props.put("value.serializer", valueSerializer);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(topic, payloadJson));
        } catch (Exception e) {
            throw new RuntimeException("❌ Kafka publish failed: " + e.getMessage(), e);
        }
    }
}
