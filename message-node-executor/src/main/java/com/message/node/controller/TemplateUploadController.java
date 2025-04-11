package com.message.node.controller;

import com.notification.common.dto.CachedStorageClient;
import com.message.node.factory.FileUploaderFactory;
import com.message.node.manager.FileStorageConnectionPoolManager;
import com.notification.common.model.FileStorageConfig;
import com.notification.common.model.TemplateEntity;
import com.message.node.service.FileStorageConfigService;
import com.message.node.service.TemplateService;
import com.notification.common.service.upload.FileUploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/template")
@RequiredArgsConstructor
public class TemplateUploadController {

    private static final long MAX_TOTAL_SIZE = 20 * 1024 * 1024; // 20 MB

    @Value("${upload.directory}")
    private String uploadDir;

    @Value("${upload.strategy:server}")
    private String uploadStrategy;

    private final TemplateService templateService;
    private final FileUploaderFactory uploaderFactory;
    private final FileStorageConfigService configService;
    private final FileStorageConnectionPoolManager fileStorageConnectionPoolManager;

    @PostMapping("/upload")
    public Mono<ResponseEntity<List<String>>> uploadFiles(@RequestParam("files") MultipartFile[] files,
                                                          @RequestParam("fileStorageId") String fileStorageId) {
        log.info("📁 File upload requested with fileStorageId: {}", fileStorageId);

        return configService.getById(fileStorageId)
                .flatMap(fileStorageConnectionPoolManager::getClient)
                .publishOn(Schedulers.boundedElastic()) // Switch to blocking-friendly thread
                .flatMap(cachedClient -> {
                    FileUploader uploader = cachedClient.getUploader();
                    Map<String, Object> properties = cachedClient.getProperties();

                    return Mono.fromCallable(() -> {
                        try {
                            List<String> paths = uploader.uploadFiles(files, properties);
                            log.info("✅ Uploaded {} files using uploader: {}", files.length, uploader.getClass().getSimpleName());
                            return ResponseEntity.ok(paths);
                        } catch (IOException e) {
                            log.error("❌ File upload failed", e);
                            throw new RuntimeException("File upload failed", e);
                        }
                    });
                });
    }

    @PostMapping("/save")
    public Mono<ResponseEntity<TemplateEntity>> saveTemplate(@RequestBody TemplateEntity template) {
        return templateService.saveTemplate(template)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{templateId}")
    public Mono<ResponseEntity<TemplateEntity>> getTemplate(@PathVariable String templateId) {
        return templateService.getTemplateById(templateId)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping("/all")
    public Flux<TemplateEntity> getAllTemplates() {
        return templateService.getAllTemplates();
    }

    @DeleteMapping("/{templateId}")
    public Mono<ResponseEntity<String>> deleteTemplate(@PathVariable String templateId) {
        return templateService.deleteTemplate(templateId)
                .thenReturn(ResponseEntity.ok("Template deleted successfully"));
    }

    @PutMapping("/{templateId}")
    public Mono<ResponseEntity<TemplateEntity>> updateTemplate(@PathVariable String templateId,
                                                               @RequestBody TemplateEntity updated) {
        return templateService.updateTemplate(templateId, updated)
                .map(ResponseEntity::ok);
    }

    private Path resolveUploadPath(String filename) {
        Path basePath = Paths.get(uploadDir);
        if (!basePath.isAbsolute()) {
            basePath = Paths.get(System.getProperty("user.dir")).resolve(basePath);
        }
        return basePath.resolve(filename);
    }
}
