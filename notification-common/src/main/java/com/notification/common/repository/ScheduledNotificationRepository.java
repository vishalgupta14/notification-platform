package com.notification.common.repository;

import com.notification.common.model.ScheduledNotification;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduledNotificationRepository extends MongoRepository<ScheduledNotification, String> {
    List<ScheduledNotification> findByActiveTrue();
}
