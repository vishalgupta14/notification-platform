package com.notification.common.repository;

import com.notification.common.model.FailedWebhookLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FailedWebhookLogRepository extends MongoRepository<FailedWebhookLog, String> {
}
