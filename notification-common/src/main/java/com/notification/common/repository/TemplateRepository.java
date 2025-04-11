package com.notification.common.repository;

import com.notification.common.model.TemplateEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import reactor.core.publisher.Mono;



public interface TemplateRepository extends ReactiveMongoRepository<TemplateEntity, String> {

    Mono<Boolean> existsByTemplateName(String templateName);
}
