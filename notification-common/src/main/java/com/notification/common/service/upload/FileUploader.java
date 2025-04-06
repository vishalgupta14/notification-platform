package com.notification.common.service.upload;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface FileUploader {
    List<String> uploadFiles(MultipartFile[] files, Map<String, Object> properties) throws IOException;
    String getFileUrl(String fileKey, Map<String, Object> properties);
    void deleteFile(String fileKey, Map<String, Object> properties);
    InputStream downloadFile(String key, Map<String, Object> properties) throws IOException;
}