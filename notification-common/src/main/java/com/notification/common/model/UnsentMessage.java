package com.notification.common.model;

import com.notification.common.enums.MessagingMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document("unsent_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnsentMessage {

    @Id
    private String id;

    private String queueName;
    private String message;
    private MessagingMode messagingType; // KAFKA or ACTIVEMQ or BOTH

    private LocalDateTime timestamp = LocalDateTime.now();

    private int retryCount = 0;
    private LocalDateTime nextRetryTime;
    private String lastError;

    public UnsentMessage(String queueName, String message, MessagingMode messagingType) {
        this.queueName = queueName;
        this.message = message;
        this.messagingType = messagingType;
        this.timestamp = LocalDateTime.now();
    }
}
