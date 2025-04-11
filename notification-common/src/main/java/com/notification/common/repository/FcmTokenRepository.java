package com.notification.common.repository;

import com.notification.common.model.FcmTokenEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import reactor.core.publisher.Mono;

import java.util.Optional;



public interface FcmTokenRepository extends ReactiveMongoRepository<FcmTokenEntity, String> {
    Mono<FcmTokenEntity> findByPhone(String phone);
}
