package com.notification.common.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Document(collection = "email_templates")
public class TemplateEntity {

    @Id
    private String id;

    private String templateName;
    private String emailSubject;

    private String content;
    private String cdnUrl;

    private List<FileReference> attachments;

    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
