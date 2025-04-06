package com.notification.common.repository;

import com.notification.common.model.FileStorageConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface FileStorageConfigRepository extends MongoRepository<FileStorageConfig, String> {

    Optional<FileStorageConfig> findByFileStorageNameAndIsActive(String fileStorageName, boolean isActive);

    boolean existsByFileStorageNameAndIsActive(String fileStorageName, boolean isActive);

    List<FileStorageConfig> findAllByIsActive(boolean isActive); // Optional utility method
}
