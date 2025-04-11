package com.notification.common.repository;

import com.notification.common.model.FailedQueueLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;



public interface FailedQueueLogRepository extends ReactiveMongoRepository<FailedQueueLog, String> {
}
