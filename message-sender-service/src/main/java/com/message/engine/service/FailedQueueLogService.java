package com.message.engine.service;

import com.notification.common.model.FailedQueueLog;
import com.notification.common.repository.FailedQueueLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FailedQueueLogService {

    private final FailedQueueLogRepository repository;

    public void save(FailedQueueLog log) {
        repository.save(log);
    }
}
