package com.message.engine.service.webhook;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component("http")
public class HttpWebhookSender implements WebhookSender {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void sendWebhook(Map<String, Object> config, String to, String messageBody) {
        String endpoint = to;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(messageBody, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(endpoint, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Webhook failed with status: " + response.getStatusCode());
        }
    }
}
