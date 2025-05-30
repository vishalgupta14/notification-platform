package com.notification.common.repository;

import com.notification.common.model.FailedQueueLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FailedQueueLogRepository extends MongoRepository<FailedQueueLog, String> {
}
