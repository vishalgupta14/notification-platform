package com.message.node.producer;

public interface MessageProducer {
    void sendMessage(String queueName, String message, boolean isPubSub);
}
