package com.message.engine.service;

import com.notification.common.model.FailedAttachmentLog;
import com.notification.common.repository.FailedAttachmentLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FailedAttachmentLogService {

    private final FailedAttachmentLogRepository repository;
    
    public FailedAttachmentLog save(FailedAttachmentLog log) {
        return repository.save(log);
    }
    
    public List<FailedAttachmentLog> findAll() {
        return repository.findAll();
    }
    
    public Optional<FailedAttachmentLog> findById(String id) {
        return repository.findById(id);
    }
    
    public void deleteById(String id) {
        repository.deleteById(id);
    }
    
    public void deleteAll() {
        repository.deleteAll();
    }
    
    public List<FailedAttachmentLog> findByNotificationConfigId(String configId) {
        return repository.findAll().stream()
                .filter(log -> configId.equals(log.getNotificationConfigId()))
                .toList();
    }
}
