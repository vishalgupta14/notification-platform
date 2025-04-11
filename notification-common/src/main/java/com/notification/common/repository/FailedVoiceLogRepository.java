package com.notification.common.repository;

import com.notification.common.model.FailedVoiceLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;



public interface FailedVoiceLogRepository extends ReactiveMongoRepository<FailedVoiceLog, String> {
}
