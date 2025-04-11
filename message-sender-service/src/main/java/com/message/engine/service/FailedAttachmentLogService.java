package com.message.engine.service;

import com.notification.common.model.FailedAttachmentLog;
import com.notification.common.repository.FailedAttachmentLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class FailedAttachmentLogService {

    private final FailedAttachmentLogRepository repository;

    public Mono<FailedAttachmentLog> save(FailedAttachmentLog log) {
        return repository.save(log);
    }

    public Flux<FailedAttachmentLog> findAll() {
        return repository.findAll();
    }

    public Mono<FailedAttachmentLog> findById(String id) {
        return repository.findById(id);
    }

    public Mono<Void> deleteById(String id) {
        return repository.deleteById(id);
    }

    public Mono<Void> deleteAll() {
        return repository.deleteAll();
    }

    public Flux<FailedAttachmentLog> findByNotificationConfigId(String configId) {
        return repository.findByNotificationConfigId(configId);
    }
}
