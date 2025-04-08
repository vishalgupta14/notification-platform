package com.message.engine.service;

import com.notification.common.model.FailedWebhookLog;
import com.notification.common.repository.FailedWebhookLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FailedWebhookLogService {

    private final FailedWebhookLogRepository repository;

    public void save(FailedWebhookLog log) {
        repository.save(log);
    }
}
