package com.notification.common.dto;

import org.springframework.mail.javamail.JavaMailSender;

public class CachedMailSender {
    private final JavaMailSender mailSender;
    private final String configHash;

    public CachedMailSender(JavaMailSender mailSender, String configHash) {
        this.mailSender = mailSender;
        this.configHash = configHash;
    }

    public JavaMailSender getMailSender() {
        return mailSender;
    }

    public String getConfigHash() {
        return configHash;
    }
}
