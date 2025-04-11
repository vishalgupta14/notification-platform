package com.notification.common.repository;

import com.notification.common.model.NotificationConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import reactor.core.publisher.Mono;

import java.util.Optional;



public interface NotificationConfigRepository extends ReactiveMongoRepository<NotificationConfig, String> {

    Mono<NotificationConfig> findByClientNameAndChannelAndIsActive(String clientName, String channel, boolean isActive);

    Mono<Boolean> existsByClientNameAndChannelAndIsActive(String clientName, String channel, boolean isActive);
}
