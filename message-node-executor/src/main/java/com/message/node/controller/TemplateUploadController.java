package com.message.node.controller;

import com.notification.common.dto.CachedStorageClient;
import com.message.node.factory.FileUploaderFactory;
import com.message.node.manager.FileStorageConnectionPoolManager;
import com.notification.common.model.FileStorageConfig;
import com.notification.common.model.TemplateEntity;
import com.message.node.service.FileStorageConfigService;
import com.message.node.service.TemplateService;
import com.notification.common.service.upload.FileUploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/api/template")
public class TemplateUploadController {

    private static final Logger log = LoggerFactory.getLogger(TemplateUploadController.class);

    private static final long MAX_TOTAL_SIZE = 20 * 1024 * 1024; // 20 MB

    @Value("${upload.directory}")
    private String uploadDir;

    @Value("${upload.strategy:server}")
    private String uploadStrategy; // server, aws, azure

    @Autowired
    private TemplateService templateService;

    @Autowired
    private FileUploaderFactory uploaderFactory;

    @Autowired
    private FileStorageConfigService configService;

    @Autowired
    private FileStorageConnectionPoolManager fileStorageConnectionPoolManager;


    @PostMapping("/upload")
    public ResponseEntity<?> uploadFiles(@RequestParam("files") MultipartFile[] files,
                                         @RequestParam("fileStorageId") String fileStorageId) throws IOException {
        log.info("File upload requested with fileStorageId: {}", fileStorageId);
        FileStorageConfig config = configService.getById(fileStorageId);
        CachedStorageClient cachedClient = fileStorageConnectionPoolManager.getClient(config);
        FileUploader uploader = cachedClient.getUploader();
        Map<String, Object> properties = cachedClient.getProperties();
        List<String> paths = uploader.uploadFiles(files, properties);
        log.info("Uploaded {} files using '{}' strategy", files.length, config.getType());
        return ResponseEntity.ok(paths);
    }


    private Path resolveUploadPath(String filename) {
        Path basePath = Paths.get(uploadDir);
        if (!basePath.isAbsolute()) {
            basePath = Paths.get(System.getProperty("user.dir")).resolve(basePath);
        }
        return basePath.resolve(filename);
    }

    @PostMapping("/save")
    public ResponseEntity<?> saveTemplate(@RequestBody TemplateEntity template) {
        TemplateEntity templateEntity = templateService.saveTemplate(template);
        return ResponseEntity.ok(templateEntity);
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<TemplateEntity> getTemplate(@PathVariable String templateId) {
        return templateService.getTemplateById(templateId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/all")
    public ResponseEntity<List<TemplateEntity>> getAllTemplates() {
        return ResponseEntity.ok(templateService.getAllTemplates());
    }

    @DeleteMapping("/{templateId}")
    public ResponseEntity<?> deleteTemplate(@PathVariable String templateId) {
        templateService.deleteTemplate(templateId);
        return ResponseEntity.ok("Template deleted successfully");
    }

    @PutMapping("/{templateId}")
    public ResponseEntity<TemplateEntity> updateTemplate(@PathVariable String templateId, @RequestBody TemplateEntity updated) {
        return ResponseEntity.ok(templateService.updateTemplate(templateId, updated));
    }
}
