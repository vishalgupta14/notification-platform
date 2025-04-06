package com.message.engine.service;

import com.notification.common.model.FailedEmailLog;
import com.notification.common.repository.FailedEmailLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FailedEmailLogService {

    private final FailedEmailLogRepository failedEmailLogRepository;

    /**
     * Save a failed email log entry.
     */
    public FailedEmailLog save(FailedEmailLog log) {
        return failedEmailLogRepository.save(log);
    }

    /**
     * Fetch all failed email logs.
     */
    public List<FailedEmailLog> getAllFailedLogs() {
        return failedEmailLogRepository.findAll();
    }

    /**
     * Fetch a single failed email log by ID.
     */
    public Optional<FailedEmailLog> getById(String id) {
        return failedEmailLogRepository.findById(id);
    }

    /**
     * Delete a failed email log by ID.
     */
    public void deleteById(String id) {
        failedEmailLogRepository.deleteById(id);
    }

    /**
     * Retry sending a failed email (optional).
     * You could inject EmailSendService here and implement retry logic.
     */
}
