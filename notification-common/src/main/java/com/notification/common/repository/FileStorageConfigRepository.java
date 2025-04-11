package com.notification.common.repository;

import com.notification.common.model.FileStorageConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;



public interface FileStorageConfigRepository extends ReactiveMongoRepository<FileStorageConfig, String> {

    Mono<FileStorageConfig> findByFileStorageNameAndIsActive(String fileStorageName, boolean isActive);

    Mono<Boolean> existsByFileStorageNameAndIsActive(String fileStorageName, boolean isActive);

    Flux<FileStorageConfig> findAllByIsActive(boolean isActive); // Optional utility method
}
