package com.notification.common.repository;

import com.notification.common.model.FailedEmailLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FailedEmailLogRepository extends MongoRepository<FailedEmailLog, String> {
}
