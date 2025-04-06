package com.message.engine.service;

import com.notification.common.model.ScheduledNotification;
import com.notification.common.repository.ScheduledNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduledNotificationService {

    private final ScheduledNotificationRepository repository;

    public void saveScheduledEmail(ScheduledNotification request) {
        repository.save(request);
    }

    public List<ScheduledNotification> getActiveScheduledJobs() {
        return repository.findByActiveTrue();
    }
}
