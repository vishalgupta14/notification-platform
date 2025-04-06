package com.notification.common.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FileReference {
    private String fileUrl;          // Full path or blob key
    private String storageType;      // "aws", "azure", "server"
    private String fileStorageId;    // Optional: link to config used
}
