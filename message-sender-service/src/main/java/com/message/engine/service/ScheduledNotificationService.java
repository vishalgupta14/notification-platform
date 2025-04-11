package com.message.engine.service;

import com.notification.common.model.ScheduledNotification;
import com.notification.common.repository.ScheduledNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ScheduledNotificationService {

    private final ScheduledNotificationRepository repository;

    public Mono<ScheduledNotification> saveScheduledEmail(ScheduledNotification request) {
        return repository.save(request);
    }

    public Flux<ScheduledNotification> getActiveScheduledJobs() {
        return repository.findByActiveTrue();
    }
}
