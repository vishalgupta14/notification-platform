package com.notification.common.service.upload;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component("server")
public class ServerFileUploader implements FileUploader {

    @Value("${upload.directory}")
    private String defaultUploadDir;

    @Override
    public List<String> uploadFiles(MultipartFile[] files, Map<String, Object> properties) throws IOException {
        String resolvedDir = properties.getOrDefault("uploadDir", defaultUploadDir).toString();
        List<String> storedPaths = new ArrayList<>();

        for (MultipartFile file : files) {
            String filename = UUID.randomUUID() + "-" + Objects.requireNonNull(file.getOriginalFilename());
            Path path = Paths.get(resolvedDir, filename);
            Files.createDirectories(path.getParent());
            Files.write(path, file.getBytes());

            String uri = getFileUrl(filename, properties);
            storedPaths.add(uri);
        }

        return storedPaths;
    }

    @Override
    public String getFileUrl(String filename, Map<String, Object> properties) {
        String uploadDir = properties.getOrDefault("uploadDir", defaultUploadDir).toString();
        Path path = Paths.get(uploadDir);

        String lastFolder = path.getFileName().toString();

        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();

        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }

        return baseUrl + lastFolder + "/" + filename;
    }

    @Override
    public void deleteFile(String filename, Map<String, Object> properties) {
        String resolvedDir = properties.getOrDefault("uploadDir", defaultUploadDir).toString();
        Path filePath = Paths.get(resolvedDir, filename);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file from server: " + filename, e);
        }
    }

    @Override
    public InputStream downloadFile(String filename, Map<String, Object> properties) throws IOException {
        String resolvedDir = properties.getOrDefault("uploadDir", defaultUploadDir).toString();
        Path filePath = Paths.get(resolvedDir, filename);

        if (!Files.exists(filePath)) {
            throw new IOException("File not found on server: " + filePath);
        }

        return Files.newInputStream(filePath);
    }
}
