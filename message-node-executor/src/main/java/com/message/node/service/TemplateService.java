package com.message.node.service;

import com.notification.common.dto.CachedStorageClient;
import com.message.node.factory.FileUploaderFactory;
import com.message.node.manager.FileStorageConnectionPoolManager;
import com.notification.common.model.FileReference;
import com.notification.common.model.FileStorageConfig;
import com.notification.common.model.TemplateEntity;
import com.notification.common.repository.TemplateRepository;
import com.notification.common.service.upload.FileUploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository repository;
    private final FileUploaderFactory uploaderFactory;
    private final FileStorageConfigService fileStorageConfigService;
    private final FileStorageConnectionPoolManager fileStorageConnectionPoolManager;

    public Mono<TemplateEntity> saveTemplate(TemplateEntity template) {
        return repository.existsByTemplateName(template.getTemplateName())
                .flatMap(exists -> {
                    if (exists) {
                        log.error("Duplicate template attempted for name={}", template.getTemplateName());
                        return Mono.error(new IllegalStateException("Template with name '" + template.getTemplateName() + "' already exists."));
                    }

                    template.setCreatedAt(LocalDateTime.now());
                    log.info("Saving new template with name={}", template.getTemplateName());
                    return repository.save(template);
                });
    }

    public Mono<TemplateEntity> getTemplateById(String templateId) {
        return repository.findById(templateId);
    }

    public Flux<TemplateEntity> getAllTemplates() {
        return repository.findAll();
    }

    public Mono<Void> deleteTemplate(String templateId) {
        return repository.findById(templateId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Template not found: " + templateId)))
                .flatMap(template -> {
                    if (template.getAttachments() != null && !template.getAttachments().isEmpty()) {
                        return Flux.fromIterable(template.getAttachments())
                                .flatMap(ref ->
                                        fileStorageConfigService.getById(ref.getFileStorageId())
                                                .flatMap(config -> fileStorageConnectionPoolManager.getClient(config))
                                                .flatMap(cachedClient -> {
                                                    try {
                                                        FileUploader uploader = cachedClient.getUploader();
                                                        Map<String, Object> properties = cachedClient.getProperties();
                                                        String keyOrFilename = extractKey(ref.getFileUrl());
                                                        uploader.deleteFile(keyOrFilename, properties);
                                                    } catch (Exception e) {
                                                        log.warn("Failed to delete file from storage: {}", ref.getFileUrl(), e);
                                                    }
                                                    return Mono.empty(); // continue even if one fails
                                                })
                                                .onErrorResume(e -> {
                                                    log.warn("Error processing attachment: {}", ref.getFileUrl(), e);
                                                    return Mono.empty(); // continue processing others
                                                })
                                )
                                .then(repository.deleteById(templateId));
                    } else {
                        return repository.deleteById(templateId);
                    }
                })
                .doOnSuccess(unused -> log.info("Deleted template with id={}", templateId));
    }



    private String extractKey(String fileUrl) {
        if (fileUrl == null || !fileUrl.contains("/")) return fileUrl;
        return fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
    }

    public Mono<TemplateEntity> updateTemplate(String templateId, TemplateEntity updated) {
        return repository.findById(templateId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Template not found: " + templateId)))
                .flatMap(existing -> {
                    if (!existing.getTemplateName().equals(updated.getTemplateName())) {
                        return repository.existsByTemplateName(updated.getTemplateName())
                                .flatMap(nameExists -> {
                                    if (nameExists) {
                                        log.error("Template name conflict during update for name={}", updated.getTemplateName());
                                        return Mono.error(new IllegalStateException("Another template with name '" + updated.getTemplateName() + "' already exists."));
                                    }

                                    return doUpdate(templateId, updated, existing);
                                });
                    }

                    return doUpdate(templateId, updated, existing);
                });
    }

    private Mono<TemplateEntity> doUpdate(String templateId, TemplateEntity updated, TemplateEntity existing) {
        updated.setId(templateId);
        updated.setCreatedAt(existing.getCreatedAt());
        updated.setUpdatedAt(LocalDateTime.now());
        log.info("Updating template with id={}, name={}", templateId, updated.getTemplateName());
        return repository.save(updated);
    }
}
