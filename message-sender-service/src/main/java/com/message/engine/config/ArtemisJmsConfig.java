package com.message.engine.config;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

@Configuration
public class ArtemisJmsConfig {

    private static final Logger log = LoggerFactory.getLogger(ArtemisJmsConfig.class);

    @Bean
    public DefaultJmsListenerContainerFactory queueListenerFactory(ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setPubSubDomain(false); // Queue mode
        factory.setConcurrency("5-10"); // Optional: scale consumers
        factory.setErrorHandler(t -> log.error("❌ JMS Listener Error [Queue]: ", t));
        factory.setSessionAcknowledgeMode(Session.AUTO_ACKNOWLEDGE); // or CLIENT_ACKNOWLEDGE
        return factory;
    }

    @Bean
    public DefaultJmsListenerContainerFactory topicListenerFactory(ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setPubSubDomain(true); // Topic mode
        factory.setConcurrency("2-5"); // Optional
        factory.setErrorHandler(t -> log.error("❌ JMS Listener Error [Topic]: ", t));
        factory.setSessionAcknowledgeMode(Session.AUTO_ACKNOWLEDGE);
        return factory;
    }
}
