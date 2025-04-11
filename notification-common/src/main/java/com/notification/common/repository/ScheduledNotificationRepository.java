package com.notification.common.repository;

import com.notification.common.model.ScheduledNotification;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import reactor.core.publisher.Flux;

import java.util.List;


public interface ScheduledNotificationRepository extends ReactiveMongoRepository<ScheduledNotification, String> {
    Flux<ScheduledNotification> findByActiveTrue();
}
