package com.notification.common.repository;

import com.notification.common.model.FailedAttachmentLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FailedAttachmentLogRepository extends MongoRepository<FailedAttachmentLog, String> {
}
