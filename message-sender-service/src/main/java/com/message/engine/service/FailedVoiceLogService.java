package com.message.engine.service;

import com.notification.common.model.FailedVoiceLog;
import com.notification.common.repository.FailedVoiceLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FailedVoiceLogService {

    private final FailedVoiceLogRepository repository;

    public void save(FailedVoiceLog log) {
        repository.save(log);
    }

}
