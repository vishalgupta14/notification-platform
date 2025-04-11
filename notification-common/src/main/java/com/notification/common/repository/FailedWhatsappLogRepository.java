package com.notification.common.repository;

import com.notification.common.model.FailedWhatsappLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;




public interface FailedWhatsappLogRepository extends ReactiveMongoRepository<FailedWhatsappLog, String> {
}
