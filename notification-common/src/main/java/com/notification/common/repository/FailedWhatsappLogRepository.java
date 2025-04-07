package com.notification.common.repository;

import com.notification.common.model.FailedWhatsappLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FailedWhatsappLogRepository extends MongoRepository<FailedWhatsappLog, String> {
}
