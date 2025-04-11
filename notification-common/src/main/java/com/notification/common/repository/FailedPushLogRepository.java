package com.notification.common.repository;

import com.notification.common.model.FailedPushLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;



public interface FailedPushLogRepository extends ReactiveMongoRepository<FailedPushLog, String> {
}
