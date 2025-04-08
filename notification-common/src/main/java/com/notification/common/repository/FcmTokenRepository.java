package com.notification.common.repository;

import com.notification.common.model.FcmTokenEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface FcmTokenRepository extends MongoRepository<FcmTokenEntity, String> {
    Optional<FcmTokenEntity> findByPhone(String phone);
}
