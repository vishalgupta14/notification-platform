package com.message.engine.service;

import com.notification.common.model.FailedSmsLog;
import com.notification.common.repository.FailedSmsLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FailedSmsLogService {

    private final FailedSmsLogRepository failedSmsLogRepository;

    public FailedSmsLog save(FailedSmsLog failedSmsLog) {
        return failedSmsLogRepository.save(failedSmsLog);
    }
}
