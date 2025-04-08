package com.notification.common.repository;

import com.notification.common.model.FailedPushLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FailedPushLogRepository extends MongoRepository<FailedPushLog, String> {
}
