package com.notification.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "fcm_tokens")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FcmTokenEntity {
    @Id
    private String id;
    private String phone;
    private String fcmToken;
    private long createdAt = System.currentTimeMillis();
}
