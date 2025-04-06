package com.notification.common.dto;

import com.notification.common.service.upload.FileUploader;

import java.util.Map;

public class CachedStorageClient {
    private final FileUploader uploader;
    private final Map<String, Object> properties;
    private final String configHash;

    public CachedStorageClient(FileUploader uploader, Map<String, Object> properties, String configHash) {
        this.uploader = uploader;
        this.properties = properties;
        this.configHash = configHash;
    }

    public FileUploader getUploader() {
        return uploader;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public String getConfigHash() {
        return configHash;
    }
}
