package com.message.engine.service.queue;

import org.apache.kafka.clients.producer.KafkaProducer;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class KafkaProducerManager {

    private final Map<String, KafkaProducer<String, String>> producerCache = new ConcurrentHashMap<>();

    public KafkaProducer<String, String> getOrCreateProducer(Map<String, Object> config) {
        String cacheKey = config.get("bootstrapServers").toString();

        return producerCache.computeIfAbsent(cacheKey, k -> {
            Properties props = new Properties();
            props.put("bootstrap.servers", config.get("bootstrapServers"));
            props.put("client.id", config.getOrDefault("clientId", "default-client"));
            props.put("acks", config.getOrDefault("acks", "all"));

            props.put("key.serializer", config.getOrDefault(
                    "key.serializer", "org.apache.kafka.common.serialization.StringSerializer"));
            props.put("value.serializer", config.getOrDefault(
                    "value.serializer", "org.apache.kafka.common.serialization.StringSerializer"));

            return new KafkaProducer<>(props);
        });
    }

    public void closeAll() {
        producerCache.values().forEach(KafkaProducer::close);
    }

    public void evict(String bootstrapServers) {
        KafkaProducer<String, String> producer = producerCache.remove(bootstrapServers);
        if (producer != null) {
            producer.close();
        }
    }
}
