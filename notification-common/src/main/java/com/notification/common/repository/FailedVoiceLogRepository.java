package com.notification.common.repository;

import com.notification.common.model.FailedVoiceLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FailedVoiceLogRepository extends MongoRepository<FailedVoiceLog, String> {
}
