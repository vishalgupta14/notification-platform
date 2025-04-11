package com.message.engine.service.queue;

import jakarta.jms.*;
import lombok.RequiredArgsConstructor;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("activemq")
@RequiredArgsConstructor
public class ActiveMqMessagePublisher implements MessagePublisher {

    private final ActiveMqConnectionFactoryManager factoryManager;

    @Override
    public void publish(Map<String, Object> config, String destinationName, String payloadJson) {
        String brokerUrl = config.get("brokerUrl").toString();
        String username = config.getOrDefault("username", "").toString();
        String password = config.getOrDefault("password", "").toString();
        boolean isTopic = Boolean.parseBoolean(config.getOrDefault("isTopic", "false").toString());

        String deliveryModeStr = config.getOrDefault("deliveryMode", "PERSISTENT").toString().toUpperCase();
        int deliveryMode = "NON_PERSISTENT".equals(deliveryModeStr) ? DeliveryMode.NON_PERSISTENT : DeliveryMode.PERSISTENT;

        String clientId = config.getOrDefault("clientId", "").toString();

        ActiveMQConnectionFactory factory = factoryManager.getFactory(brokerUrl);

        try (Connection connection = factory.createConnection(username, password)) {
            if (!clientId.isBlank()) {
                connection.setClientID(clientId);
            }
            connection.start();

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination destination = isTopic
                    ? session.createTopic(destinationName)
                    : session.createQueue(destinationName);

            MessageProducer producer = session.createProducer(destination);
            producer.setDeliveryMode(deliveryMode);

            TextMessage message = session.createTextMessage(payloadJson);
            producer.send(message);

        } catch (Exception e) {
            throw new RuntimeException("❌ Failed to send message to ActiveMQ destination [" + destinationName + "]", e);
        }
    }
}
