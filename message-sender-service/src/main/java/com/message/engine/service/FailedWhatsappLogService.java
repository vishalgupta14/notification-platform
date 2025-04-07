package com.message.engine.service;

import com.notification.common.model.FailedWhatsappLog;
import com.notification.common.repository.FailedWhatsappLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FailedWhatsappLogService {

    private final FailedWhatsappLogRepository repository;

    public void save(FailedWhatsappLog log) {
        repository.save(log);
    }
}
