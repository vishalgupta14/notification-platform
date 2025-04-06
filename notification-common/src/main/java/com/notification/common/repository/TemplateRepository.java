package com.notification.common.repository;

import com.notification.common.model.TemplateEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TemplateRepository extends MongoRepository<TemplateEntity, String> {

    boolean existsByTemplateName(String templateName);
}
