package com.message.engine.service;

import com.notification.common.model.FailedSmsLog;
import com.notification.common.repository.FailedSmsLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class FailedSmsLogService {

    private final FailedSmsLogRepository failedSmsLogRepository;

    public void save(FailedSmsLog failedSmsLog) {
         failedSmsLogRepository.save(failedSmsLog);
    }
}
