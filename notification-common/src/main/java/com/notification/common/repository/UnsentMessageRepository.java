package com.notification.common.repository;

import com.notification.common.model.UnsentMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;




public interface UnsentMessageRepository extends ReactiveMongoRepository<UnsentMessage, String> {
}