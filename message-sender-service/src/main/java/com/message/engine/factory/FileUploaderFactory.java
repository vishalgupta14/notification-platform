package com.message.engine.factory;

import com.notification.common.service.upload.FileUploader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class FileUploaderFactory {

    @Autowired
    private ApplicationContext context;

    public FileUploader getUploader(String strategy) {
        return (FileUploader) context.getBean(strategy.toLowerCase());
    }
}