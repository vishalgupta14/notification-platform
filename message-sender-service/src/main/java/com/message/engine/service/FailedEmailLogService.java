package com.message.engine.service;

import com.notification.common.model.FailedEmailLog;
import com.notification.common.repository.FailedEmailLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class FailedEmailLogService {

    private final FailedEmailLogRepository failedEmailLogRepository;

    /**
     * Save a failed email log entry.
     */
    public Mono<FailedEmailLog> save(FailedEmailLog log) {
        return failedEmailLogRepository.save(log);
    }

    /**
     * Fetch all failed email logs.
     */
    public Flux<FailedEmailLog> getAllFailedLogs() {
        return failedEmailLogRepository.findAll();
    }

    /**
     * Fetch a single failed email log by ID.
     */
    public Mono<FailedEmailLog> getById(String id) {
        return failedEmailLogRepository.findById(id);
    }

    /**
     * Delete a failed email log by ID.
     */
    public Mono<Void> deleteById(String id) {
        return failedEmailLogRepository.deleteById(id);
    }

}
