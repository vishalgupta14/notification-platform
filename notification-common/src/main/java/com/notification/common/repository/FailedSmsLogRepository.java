package com.notification.common.repository;

import com.notification.common.model.FailedSmsLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FailedSmsLogRepository extends MongoRepository<FailedSmsLog, String> {
    // Optionally add custom find methods
}
