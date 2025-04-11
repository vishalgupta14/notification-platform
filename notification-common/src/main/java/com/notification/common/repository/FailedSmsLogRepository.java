package com.notification.common.repository;

import com.notification.common.model.FailedSmsLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;



public interface FailedSmsLogRepository extends ReactiveMongoRepository<FailedSmsLog, String> {
}
