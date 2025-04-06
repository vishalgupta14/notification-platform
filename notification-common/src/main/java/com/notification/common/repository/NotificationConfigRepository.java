package com.notification.common.repository;

import com.notification.common.model.NotificationConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface NotificationConfigRepository extends MongoRepository<NotificationConfig, String> {

    Optional<NotificationConfig> findByClientNameAndChannelAndIsActive(String clientName, String channel, boolean isActive);

    boolean existsByClientNameAndChannelAndIsActive(String clientName, String channel, boolean isActive);
}
