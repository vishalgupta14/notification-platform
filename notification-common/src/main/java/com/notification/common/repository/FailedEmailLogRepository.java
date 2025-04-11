package com.notification.common.repository;

import com.notification.common.model.FailedEmailLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;



public interface FailedEmailLogRepository extends ReactiveMongoRepository<FailedEmailLog, String> {
}
