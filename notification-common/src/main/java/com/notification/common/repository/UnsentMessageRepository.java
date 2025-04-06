package com.notification.common.repository;

import com.notification.common.model.UnsentMessage;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UnsentMessageRepository extends MongoRepository<UnsentMessage, String> {
}