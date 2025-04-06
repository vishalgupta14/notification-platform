package com.notification.common.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Getter
@Setter
@Document(collection = "file_storage_configs")
public class FileStorageConfig {

    @Id
    private String id;
    private String fileStorageName;
    private String type; // aws, azure, server
    private Map<String, Object> properties;
    private String description;
    private boolean isActive;
}