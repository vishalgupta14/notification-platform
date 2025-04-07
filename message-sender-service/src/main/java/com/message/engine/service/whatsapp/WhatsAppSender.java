package com.message.engine.service.whatsapp;

import java.io.File;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;

public interface WhatsAppSender {
    void sendWhatsApp(Map<String, Object> config, String to, String message, List<File> attachments) throws MalformedURLException;
}
