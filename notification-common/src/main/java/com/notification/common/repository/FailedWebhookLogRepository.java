package com.notification.common.repository;

import com.notification.common.model.FailedWebhookLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;



public interface FailedWebhookLogRepository extends ReactiveMongoRepository<FailedWebhookLog, String> {
}
