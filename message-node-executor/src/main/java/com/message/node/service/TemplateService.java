package com.message.node.service;

import com.notification.common.dto.CachedStorageClient;
import com.message.node.factory.FileUploaderFactory;
import com.message.node.manager.FileStorageConnectionPoolManager;
import com.notification.common.model.FileReference;
import com.notification.common.model.FileStorageConfig;
import com.notification.common.model.TemplateEntity;
import com.notification.common.repository.TemplateRepository;
import com.notification.common.service.upload.FileUploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);

    @Autowired
    private TemplateRepository repository;

    @Autowired
    private FileUploaderFactory uploaderFactory;

    @Autowired
    private FileStorageConfigService fileStorageConfigService;

    @Autowired
    private FileStorageConnectionPoolManager fileStorageConnectionPoolManager;

    public TemplateEntity saveTemplate(TemplateEntity template) {
        boolean exists = repository.existsByTemplateName(template.getTemplateName());

        if (exists) {
            log.error("Duplicate template attempted for name={}", template.getTemplateName());
            throw new IllegalStateException("Template with name '" + template.getTemplateName() + "' already exists.");
        }

        template.setCreatedAt(LocalDateTime.now());
        log.info("Saving new template with name={}", template.getTemplateName());
        return repository.save(template);
    }

    public Optional<TemplateEntity> getTemplateById(String templateId) {
        return repository.findById(templateId);
    }

    public List<TemplateEntity> getAllTemplates() {
        return repository.findAll();
    }

    public void deleteTemplate(String templateId) {
        TemplateEntity template = repository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        if (template.getAttachments() != null) {
            for (FileReference ref : template.getAttachments()) {
                try {
                    FileStorageConfig config = fileStorageConfigService.getById(ref.getFileStorageId());
                    CachedStorageClient cachedClient = fileStorageConnectionPoolManager.getClient(config);
                    FileUploader uploader = cachedClient.getUploader();
                    Map<String, Object> properties = cachedClient.getProperties();

                    String keyOrFilename = extractKey(ref.getFileUrl());
                    uploader.deleteFile(keyOrFilename, properties);

                } catch (Exception e) {
                    log.warn("Failed to delete file from storage: {}", ref.getFileUrl(), e);
                }
            }
        }

        repository.deleteById(templateId);
        log.info("Deleted template with id={}", templateId);
    }

    private String extractKey(String fileUrl) {
        if (fileUrl == null || !fileUrl.contains("/")) return fileUrl;
        return fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
    }

    public TemplateEntity updateTemplate(String templateId, TemplateEntity updated) {
        TemplateEntity existing = repository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        // Optional: If template name should remain unique during update
        if (!existing.getTemplateName().equals(updated.getTemplateName()) &&
                repository.existsByTemplateName(updated.getTemplateName())) {
            log.error("Template name conflict during update for name={}", updated.getTemplateName());
            throw new IllegalStateException("Another template with name '" + updated.getTemplateName() + "' already exists.");
        }

        updated.setId(templateId);
        updated.setCreatedAt(existing.getCreatedAt()); // preserve original creation time
        updated.setUpdatedAt(LocalDateTime.now());     // update modified time
        log.info("Updating template with id={}, name={}", templateId, updated.getTemplateName());
        return repository.save(updated);
    }
}
