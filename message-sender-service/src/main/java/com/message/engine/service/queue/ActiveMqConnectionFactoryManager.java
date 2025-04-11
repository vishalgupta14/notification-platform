package com.message.engine.service.queue;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ActiveMqConnectionFactoryManager {

    private final Map<String, ActiveMQConnectionFactory> cache = new ConcurrentHashMap<>();

    public ActiveMQConnectionFactory getFactory(String brokerUrl) {
        return cache.computeIfAbsent(brokerUrl, url -> {
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(url);
            factory.setCallTimeout(10000); // Optional tuning
            factory.setBlockOnAcknowledge(false);
            return factory;
        });
    }
}
