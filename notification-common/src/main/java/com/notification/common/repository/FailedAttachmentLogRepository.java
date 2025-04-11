package com.notification.common.repository;

import com.notification.common.model.FailedAttachmentLog;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import reactor.core.publisher.Flux;


public interface FailedAttachmentLogRepository extends ReactiveMongoRepository<FailedAttachmentLog, String> {
    Flux<FailedAttachmentLog> findByNotificationConfigId(String notificationConfigId);
}
