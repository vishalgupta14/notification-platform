package com.message.engine.service;

import com.notification.common.model.FailedPushLog;
import com.notification.common.repository.FailedPushLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FailedPushLogService {

    private final FailedPushLogRepository repository;

    public void save(FailedPushLog log) {
        repository.save(log);
    }
}
